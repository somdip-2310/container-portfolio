package dev.somdip.containerplatform.service;

import dev.somdip.containerplatform.model.Container;
import dev.somdip.containerplatform.model.Deployment;
import dev.somdip.containerplatform.repository.ContainerRepository;
import dev.somdip.containerplatform.repository.DeploymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.*;
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class EcsService {
    private static final Logger log = LoggerFactory.getLogger(EcsService.class);
    
    private final EcsClient ecsClient;
    private final ElasticLoadBalancingV2Client elbClient;
    private final TargetGroupService targetGroupService;
    private final DeploymentRepository deploymentRepository;
    private final ContainerRepository containerRepository;
    
    @Value("${aws.ecs.cluster}")
    private String clusterName;
    
    @Value("${aws.ecs.subnet1}")
    private String subnet1;
    
    @Value("${aws.ecs.subnet2}")
    private String subnet2;
    
    @Value("${aws.ecs.securityGroup}")
    private String securityGroup;
    
    @Value("${aws.ecs.taskRoleArn}")
    private String taskRoleArn;
    
    @Value("${aws.ecs.executionRoleArn}")
    private String executionRoleArn;
    
    @Value("${aws.alb.targetGroup.arn}")
    private String targetGroupArn;
    
    @Value("${aws.alb.targetGroup.users.arn}")
    private String userContainersTargetGroupArn;
    
    @Value("${aws.ecr.healthProxyImage}")
    private String healthProxyImage;

    @Value("${aws.cloudwatch.logGroup.users}")
    private String logGroup;

    // Configurable timeout for service stabilization (in seconds)
    // Default: 15 minutes (900s) to handle large applications and slow startups
    // Can be increased for very large deployments via application.properties
    @Value("${aws.ecs.deployment.timeout.seconds:900}")
    private int deploymentTimeoutSeconds;

    public EcsService(EcsClient ecsClient, 
                     ElasticLoadBalancingV2Client elbClient,
                     TargetGroupService targetGroupService,
                     DeploymentRepository deploymentRepository,
                     ContainerRepository containerRepository) {
        this.ecsClient = ecsClient;
        this.elbClient = elbClient;
        this.targetGroupService = targetGroupService;
        this.deploymentRepository = deploymentRepository;
        this.containerRepository = containerRepository;
    }
    
    public Deployment deployContainer(Container container, String userId) {
        log.info("Starting deployment for container {} by user {}", container.getContainerId(), userId);
        
        // Create deployment record
        Deployment deployment = createDeploymentRecord(container, userId);
        
        try {
            // Step 1: Create task definition
            updateDeploymentStep(deployment, "CREATE_TASK_DEFINITION", Deployment.DeploymentStep.StepStatus.IN_PROGRESS);
            String taskDefinitionArn = createTaskDefinition(container);
            container.setTaskDefinitionArn(taskDefinitionArn);
            updateDeploymentStep(deployment, "CREATE_TASK_DEFINITION", Deployment.DeploymentStep.StepStatus.COMPLETED);
            
            // Step 2: Create or update service
            updateDeploymentStep(deployment, "CREATE_SERVICE", Deployment.DeploymentStep.StepStatus.IN_PROGRESS);
            String serviceArn = createOrUpdateService(container);
            container.setServiceArn(serviceArn);
            updateDeploymentStep(deployment, "CREATE_SERVICE", Deployment.DeploymentStep.StepStatus.COMPLETED);
            
            // Step 3: Wait for service to stabilize
            updateDeploymentStep(deployment, "WAIT_FOR_STABLE", Deployment.DeploymentStep.StepStatus.IN_PROGRESS);
            waitForServiceStable(serviceArn);
            updateDeploymentStep(deployment, "WAIT_FOR_STABLE", Deployment.DeploymentStep.StepStatus.COMPLETED);
            
            // Step 4: Get running task
            updateDeploymentStep(deployment, "GET_TASK_INFO", Deployment.DeploymentStep.StepStatus.IN_PROGRESS);
            String taskArn = getRunningTaskArn(serviceArn);
            container.setTaskArn(taskArn);
            updateDeploymentStep(deployment, "GET_TASK_INFO", Deployment.DeploymentStep.StepStatus.COMPLETED);
            
            // Step 5: Register with target group (without port parameter)
         // Step 5: Register with target group with port
            updateDeploymentStep(deployment, "REGISTER_TARGET_GROUP", Deployment.DeploymentStep.StepStatus.IN_PROGRESS);
            targetGroupService.registerTaskWithTargetGroup(userContainersTargetGroupArn, taskArn, container.getPort());
            container.setTargetGroupArn(userContainersTargetGroupArn);
            updateDeploymentStep(deployment, "REGISTER_TARGET_GROUP", Deployment.DeploymentStep.StepStatus.COMPLETED);
            
            // Step 6: Configure health check
            updateDeploymentStep(deployment, "CONFIGURE_HEALTH_CHECK", Deployment.DeploymentStep.StepStatus.IN_PROGRESS);
            configureHealthCheck(container);
            updateDeploymentStep(deployment, "CONFIGURE_HEALTH_CHECK", Deployment.DeploymentStep.StepStatus.COMPLETED);
            
            // Mark deployment as completed
            deployment.setStatus(Deployment.DeploymentStatus.COMPLETED);
            deployment.setCompletedAt(Instant.now());
            deployment.setDurationMillis(
                Instant.now().toEpochMilli() - deployment.getStartedAt().toEpochMilli()
            );
            deploymentRepository.save(deployment);
            
            log.info("Container deployed successfully: {}", container.getContainerId());
            return deployment;
            
        } catch (Exception e) {
            log.error("Failed to deploy container: {}", container.getContainerId(), e);
            
            // Mark deployment as failed
            deployment.setStatus(Deployment.DeploymentStatus.FAILED);
            deployment.setErrorMessage(e.getMessage());
            deployment.setErrorCode(e.getClass().getSimpleName());
            deployment.setCompletedAt(Instant.now());
            deploymentRepository.save(deployment);
            
            throw new RuntimeException("ECS deployment failed", e);
        }
    }
    
    private Deployment createDeploymentRecord(Container container, String userId) {
        Deployment deployment = new Deployment();
        deployment.setDeploymentId(UUID.randomUUID().toString());
        deployment.setContainerId(container.getContainerId());
        deployment.setContainerName(container.getContainerName());
        deployment.setUserId(container.getUserId());
        deployment.setInitiatedBy(userId);
        deployment.setType(Deployment.DeploymentType.CREATE);
        deployment.setStatus(Deployment.DeploymentStatus.IN_PROGRESS);
        deployment.setStartedAt(Instant.now());
        deployment.setCreatedAt(Instant.now());
        deployment.setNewImage(container.getImage() + ":" + container.getImageTag());
        
        // Initialize deployment steps
        List<Deployment.DeploymentStep> steps = new ArrayList<>();
        steps.add(createStep("CREATE_TASK_DEFINITION", "Creating ECS task definition"));
        steps.add(createStep("CREATE_SERVICE", "Creating ECS service"));
        steps.add(createStep("WAIT_FOR_STABLE", "Waiting for service to stabilize"));
        steps.add(createStep("GET_TASK_INFO", "Getting task information"));
        steps.add(createStep("REGISTER_TARGET_GROUP", "Registering with load balancer"));
        steps.add(createStep("CONFIGURE_HEALTH_CHECK", "Configuring health checks"));
        deployment.setSteps(steps);
        
        // Set deployment strategy
        Deployment.DeploymentStrategy strategy = new Deployment.DeploymentStrategy();
        strategy.setType("ROLLING_UPDATE");
        strategy.setHealthCheckGracePeriod(60);
        strategy.setEnableCircuitBreaker(true);
        deployment.setStrategy(strategy);
        
        return deploymentRepository.save(deployment);
    }
    
    private Deployment.DeploymentStep createStep(String name, String message) {
        Deployment.DeploymentStep step = new Deployment.DeploymentStep();
        step.setStepName(name);
        step.setMessage(message);
        step.setStatus(Deployment.DeploymentStep.StepStatus.PENDING);
        return step;
    }
    
    private void updateDeploymentStep(Deployment deployment, String stepName, 
                                     Deployment.DeploymentStep.StepStatus status) {
        deployment.getSteps().stream()
            .filter(step -> step.getStepName().equals(stepName))
            .findFirst()
            .ifPresent(step -> {
                step.setStatus(status);
                if (status == Deployment.DeploymentStep.StepStatus.IN_PROGRESS) {
                    step.setStartedAt(Instant.now());
                } else if (status == Deployment.DeploymentStep.StepStatus.COMPLETED ||
                          status == Deployment.DeploymentStep.StepStatus.FAILED) {
                    step.setCompletedAt(Instant.now());
                }
            });
        deploymentRepository.save(deployment);
    }
    
    private void waitForServiceStable(String serviceArn) throws InterruptedException {
        log.info("Waiting for service to stabilize: {} (timeout: {}s)", serviceArn, deploymentTimeoutSeconds);

        int checkIntervalSeconds = 10;
        int maxAttempts = deploymentTimeoutSeconds / checkIntervalSeconds;
        int attempt = 0;
        long startTime = System.currentTimeMillis();

        while (attempt < maxAttempts) {
            DescribeServicesRequest request = DescribeServicesRequest.builder()
                .cluster(clusterName)
                .services(serviceArn)
                .build();

            DescribeServicesResponse response = ecsClient.describeServices(request);

            if (!response.services().isEmpty()) {
                software.amazon.awssdk.services.ecs.model.Service service = response.services().get(0);

                // More lenient stability check:
                // 1. Running count meets desired count
                // 2. At least one PRIMARY deployment exists (new deployment is active)
                boolean hasRunningTasks = service.runningCount() >= service.desiredCount() && service.desiredCount() > 0;
                boolean hasPrimaryDeployment = service.deployments().stream()
                    .anyMatch(d -> "PRIMARY".equals(d.status()));

                if (hasRunningTasks && hasPrimaryDeployment) {
                    long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;
                    log.info("Service is stable after {}s with {} running tasks (Deployments: {})",
                        elapsedSeconds, service.runningCount(), service.deployments().size());
                    return;
                }

                long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;
                log.info("Service not stable yet ({}s elapsed). Running: {}/{}, Deployments: {}, Status: {}",
                    elapsedSeconds, service.runningCount(), service.desiredCount(),
                    service.deployments().size(),
                    service.deployments().stream().map(d -> d.status()).collect(Collectors.joining(", ")));
            }

            attempt++;
            Thread.sleep(checkIntervalSeconds * 1000);
        }

        long totalSeconds = (System.currentTimeMillis() - startTime) / 1000;
        throw new RuntimeException(String.format(
            "Service did not stabilize within timeout (%ds / %d attempts). Elapsed: %ds",
            deploymentTimeoutSeconds, maxAttempts, totalSeconds));
    }
    
    private String getRunningTaskArn(String serviceArn) {
        ListTasksRequest request = ListTasksRequest.builder()
            .cluster(clusterName)
            .serviceName(serviceArn)
            .desiredStatus(DesiredStatus.RUNNING)
            .build();
        
        ListTasksResponse response = ecsClient.listTasks(request);
        
        if (response.taskArns().isEmpty()) {
            throw new RuntimeException("No running tasks found for service: " + serviceArn);
        }
        
        return response.taskArns().get(0);
    }
    
    private void configureHealthCheck(Container container) {
        if (container.getHealthCheck() != null) {
            // Health check configuration is done at the target group level
            // which was already configured in createTargetGroup
            log.info("Health check configured for container: {}", container.getContainerId());
        }
    }
    
    private String createTaskDefinition(Container container) {
        String family = "container-" + container.getContainerId();

        // Build user's application container definition
        ContainerDefinition userContainerDef = ContainerDefinition.builder()
            .name(container.getContainerName())
            .image(container.getImage() + ":" + container.getImageTag())
            .cpu(container.getCpu() - 64) // Reserve 64 CPU units for health-proxy
            .memory(container.getMemory() - 128) // Reserve 128MB for health-proxy
            .essential(true)
            .portMappings(PortMapping.builder()
                .containerPort(container.getPort())
                .protocol("tcp")
                .build())
            .environment(convertEnvironmentVariables(container))
            .logConfiguration(LogConfiguration.builder()
                .logDriver("awslogs")
                .options(Map.of(
                    "awslogs-group", logGroup,
                    "awslogs-region", "us-east-1",
                    "awslogs-stream-prefix", container.getContainerId() + "/app"
                ))
                .build())
            .build();

        // Build health-proxy sidecar container definition
        ContainerDefinition healthProxyDef = ContainerDefinition.builder()
            .name("health-proxy")
            .image(healthProxyImage)
            .cpu(64)
            .memory(128)
            .essential(false) // Non-essential so user app failure doesn't stop proxy
            .portMappings(PortMapping.builder()
                .containerPort(9090)
                .protocol("tcp")
                .build())
            .environment(List.of(
                KeyValuePair.builder().name("USER_APP_PORT").value(String.valueOf(container.getPort())).build(),
                KeyValuePair.builder().name("USER_APP_HOST").value("localhost").build(),
                KeyValuePair.builder().name("HEALTH_PROXY_PORT").value("9090").build()
            ))
            .logConfiguration(LogConfiguration.builder()
                .logDriver("awslogs")
                .options(Map.of(
                    "awslogs-group", logGroup,
                    "awslogs-region", "us-east-1",
                    "awslogs-stream-prefix", container.getContainerId() + "/health-proxy"
                ))
                .build())
            .healthCheck(HealthCheck.builder()
                .command("CMD-SHELL", "curl -f http://localhost:9090/health || exit 1")
                .interval(30)
                .timeout(5)
                .retries(3)
                .startPeriod(60)
                .build())
            .build();

        RegisterTaskDefinitionRequest request = RegisterTaskDefinitionRequest.builder()
            .family(family)
            .taskRoleArn(taskRoleArn)
            .executionRoleArn(executionRoleArn)
            .networkMode(NetworkMode.AWSVPC)
            .requiresCompatibilities(Compatibility.FARGATE)
            .cpu(String.valueOf(container.getCpu()))
            .memory(String.valueOf(container.getMemory()))
            .containerDefinitions(userContainerDef, healthProxyDef)
            .build();

        log.info("Created task definition with health-proxy sidecar for container: {}", container.getContainerId());
        RegisterTaskDefinitionResponse response = ecsClient.registerTaskDefinition(request);
        return response.taskDefinition().taskDefinitionArn();
    }
    
    private HealthCheck createHealthCheckConfig(Container container) {
        if (container.getHealthCheck() == null) {
            // Detect container type and use appropriate health check
            String healthCheckPath = detectHealthCheckPath(container);

            return HealthCheck.builder()
                .command("CMD-SHELL", "curl -f http://localhost:" + container.getPort() + healthCheckPath + " || exit 1")
                .interval(30)
                .timeout(5)
                .retries(3)
                .startPeriod(60)
                .build();
        }

        Container.HealthCheckConfig hc = container.getHealthCheck();
        String healthPath = hc.getPath() != null ? hc.getPath() : "/health";
        String healthCommand = "curl -f http://localhost:" + container.getPort() + healthPath + " || exit 1";

        return HealthCheck.builder()
            .command("CMD-SHELL", healthCommand)
            .interval(hc.getInterval() != null ? hc.getInterval() : 30)
            .timeout(hc.getTimeout() != null ? hc.getTimeout() : 5)
            .retries(hc.getHealthyThreshold() != null ? hc.getHealthyThreshold() : 3)
            .startPeriod(60)
            .build();
    }

    /**
     * Detect appropriate health check path based on container image type
     */
    private String detectHealthCheckPath(Container container) {
        String image = container.getImage().toLowerCase();

        // Static site servers (nginx, apache, httpd)
        if (image.contains("nginx") || image.contains("httpd") || image.contains("apache")) {
            log.info("Detected static web server (nginx/apache) for container {}, using / health check", container.getContainerId());
            return "/";
        }

        // Node.js applications
        if (image.contains("node")) {
            log.info("Detected Node.js application for container {}, using /health health check", container.getContainerId());
            return "/health";
        }

        // Python applications (Flask, Django, FastAPI)
        if (image.contains("python")) {
            log.info("Detected Python application for container {}, using /health health check", container.getContainerId());
            return "/health";
        }

        // Java applications (Spring Boot, Tomcat)
        if (image.contains("java") || image.contains("temurin") || image.contains("openjdk") || image.contains("tomcat")) {
            log.info("Detected Java application for container {}, using /actuator/health health check", container.getContainerId());
            return "/actuator/health";
        }

        // Go applications
        if (image.contains("golang") || image.contains("go:") || image.contains("/go")) {
            log.info("Detected Go application for container {}, using /health health check", container.getContainerId());
            return "/health";
        }

        // PHP applications
        if (image.contains("php")) {
            log.info("Detected PHP application for container {}, using / health check", container.getContainerId());
            return "/";
        }

        // Ruby applications (Rails)
        if (image.contains("ruby") || image.contains("rails")) {
            log.info("Detected Ruby/Rails application for container {}, using /health health check", container.getContainerId());
            return "/health";
        }

        // .NET applications
        if (image.contains("dotnet") || image.contains("aspnet")) {
            log.info("Detected .NET application for container {}, using /health health check", container.getContainerId());
            return "/health";
        }

        // Apache (another static web server)
        if (image.contains("httpd") || image.contains("apache2")) {
            log.info("Detected Apache web server for container {}, using / health check", container.getContainerId());
            return "/";
        }

        // Tomcat (Java application server)
        if (image.contains("tomcat")) {
            log.info("Detected Tomcat application server for container {}, using / health check", container.getContainerId());
            return "/";
        }

        // If we reach here, it's an unsupported type that somehow passed validation
        // This should not happen, but we'll log a warning and default to /health
        log.warn("Unknown container type for {}, this should have been caught during validation. Defaulting to /health", container.getContainerId());
        return "/health";
    }
    
    private String createOrUpdateService(Container container) {
        String serviceName = "service-" + container.getContainerId();
        
        try {
            // Check if service exists
            DescribeServicesRequest describeRequest = DescribeServicesRequest.builder()
                .cluster(clusterName)
                .services(serviceName)
                .build();
            
            DescribeServicesResponse describeResponse = ecsClient.describeServices(describeRequest);
            
            if (!describeResponse.services().isEmpty() && 
                describeResponse.services().get(0).status().equals("ACTIVE")) {
                // Update existing service
                UpdateServiceRequest updateRequest = UpdateServiceRequest.builder()
                    .cluster(clusterName)
                    .service(serviceName)
                    .taskDefinition(container.getTaskDefinitionArn())
                    .desiredCount(1)
                    .deploymentConfiguration(DeploymentConfiguration.builder()
                        .maximumPercent(200)
                        .minimumHealthyPercent(100)
                        .deploymentCircuitBreaker(DeploymentCircuitBreaker.builder()
                            .enable(true)
                            .rollback(true)
                            .build())
                        .build())
                    .build();
                
                UpdateServiceResponse updateResponse = ecsClient.updateService(updateRequest);
                return updateResponse.service().serviceArn();
            }
        } catch (Exception e) {
            log.debug("Service does not exist, creating new one");
        }
        
        // Create new service
        CreateServiceRequest createRequest = CreateServiceRequest.builder()
            .cluster(clusterName)
            .serviceName(serviceName)
            .taskDefinition(container.getTaskDefinitionArn())
            .desiredCount(1)
            .launchType(LaunchType.FARGATE)
            .networkConfiguration(NetworkConfiguration.builder()
                .awsvpcConfiguration(AwsVpcConfiguration.builder()
                    .subnets(subnet1, subnet2)
                    .securityGroups(securityGroup)
                    .assignPublicIp(AssignPublicIp.ENABLED)
                    .build())
                .build())
            .loadBalancers(LoadBalancer.builder()
                    .targetGroupArn(userContainersTargetGroupArn)
                    .containerName(container.getContainerName())
                    .containerPort(container.getPort())
                    .build())
            .healthCheckGracePeriodSeconds(60)
            .deploymentConfiguration(DeploymentConfiguration.builder()
                .maximumPercent(200)
                .minimumHealthyPercent(100)
                .deploymentCircuitBreaker(DeploymentCircuitBreaker.builder()
                    .enable(true)
                    .rollback(true)
                    .build())
                .build())
            .enableExecuteCommand(true) // Enable ECS Exec for debugging
            .build();
        
        CreateServiceResponse createResponse = ecsClient.createService(createRequest);
        return createResponse.service().serviceArn();
    }
    
    public void stopService(String serviceArn, String containerId) {
        log.info("Stopping ECS service: {} for container: {}", serviceArn, containerId);
        
        try {
            // Check service status first
            DescribeServicesRequest describeRequest = DescribeServicesRequest.builder()
                .cluster(clusterName)
                .services(serviceArn)
                .build();
            
            DescribeServicesResponse describeResponse = ecsClient.describeServices(describeRequest);
            
            if (describeResponse.services().isEmpty()) {
                log.warn("Service not found: {}", serviceArn);
                return;
            }
            
            software.amazon.awssdk.services.ecs.model.Service service = describeResponse.services().get(0);
            String serviceStatus = service.status();
            
            // Only proceed if service is ACTIVE
            if (!"ACTIVE".equals(serviceStatus)) {
                log.info("Service is not active (status: {}), skipping stop operation", serviceStatus);
                return;
            }
            
            // Get container to know the port
            Container container = containerRepository.findById(containerId)
                .orElseThrow(() -> new RuntimeException("Container not found: " + containerId));
            
            // First deregister from target group with port
            try {
                String taskArn = getRunningTaskArn(serviceArn);
                targetGroupService.deregisterTaskFromTargetGroup(userContainersTargetGroupArn, taskArn, container.getPort());
                log.info("Deregistered task {} from target group on port {}", taskArn, container.getPort());
            } catch (Exception e) {
                log.warn("Failed to deregister from target group: {}", e.getMessage());
            }
            
            // Then scale down service
            UpdateServiceRequest request = UpdateServiceRequest.builder()
                .cluster(clusterName)
                .service(serviceArn)
                .desiredCount(0)
                .build();
            
            ecsClient.updateService(request);
            log.info("Service scaled down to 0");
            
        } catch (Exception e) {
            log.error("Error stopping service: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to stop service", e);
        }
    }
    
    public void deleteService(String serviceArn, String containerId) {
        log.info("Deleting ECS service: {}", serviceArn);
        
        try {
            // Check if service exists and its status
            DescribeServicesRequest describeRequest = DescribeServicesRequest.builder()
                .cluster(clusterName)
                .services(serviceArn)
                .build();
            
            DescribeServicesResponse describeResponse = ecsClient.describeServices(describeRequest);
            
            if (describeResponse.services().isEmpty()) {
                log.info("Service not found, nothing to delete");
                return;
            }
            
            software.amazon.awssdk.services.ecs.model.Service service = describeResponse.services().get(0);
            String serviceStatus = service.status();
            
            // If service is ACTIVE, stop it first
            if ("ACTIVE".equals(serviceStatus)) {
                log.info("Service is active, stopping it first");
                stopService(serviceArn, containerId);
                
                // Wait for tasks to stop
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else {
                log.info("Service is not active (status: {}), proceeding with deletion", serviceStatus);
            }
            
            // Delete service
            DeleteServiceRequest deleteRequest = DeleteServiceRequest.builder()
                .cluster(clusterName)
                .service(serviceArn)
                .force(true)
                .build();
            
            ecsClient.deleteService(deleteRequest);
            log.info("Service deletion initiated");
            
        } catch (Exception e) {
            log.error("Error deleting service: {}", e.getMessage(), e);
            // Don't throw exception if service is already gone
            if (!e.getMessage().contains("ServiceNotFoundException")) {
                throw new RuntimeException("Failed to delete service", e);
            }
        }
    }
    
    private Collection<KeyValuePair> convertEnvironmentVariables(Container container) {
        List<KeyValuePair> envVars = new ArrayList<>();
        Map<String, String> userEnvVars = container.getEnvironmentVariables();
        
        // Add default environment variables for proper network binding (if not user-provided)
        if (userEnvVars == null || !userEnvVars.containsKey("HOST")) {
            envVars.add(KeyValuePair.builder().name("HOST").value("0.0.0.0").build());
        }
        if (userEnvVars == null || !userEnvVars.containsKey("PORT")) {
            envVars.add(KeyValuePair.builder().name("PORT").value(String.valueOf(container.getPort())).build());
        }
        // For Java Spring Boot applications
        if (userEnvVars == null || !userEnvVars.containsKey("SERVER_ADDRESS")) {
            envVars.add(KeyValuePair.builder().name("SERVER_ADDRESS").value("0.0.0.0").build());
        }
        // For .NET applications
        if (userEnvVars == null || !userEnvVars.containsKey("ASPNETCORE_URLS")) {
            envVars.add(KeyValuePair.builder()
                .name("ASPNETCORE_URLS")
                .value("http://0.0.0.0:" + container.getPort())
                .build());
        }
        
        // Add user-defined environment variables (these take precedence)
        if (userEnvVars != null) {
            userEnvVars.entrySet().forEach(entry ->
                envVars.add(KeyValuePair.builder()
                    .name(entry.getKey())
                    .value(entry.getValue())
                    .build())
            );
        }
        
        return envVars;
    }
    
    private Task getTaskDetails(String taskArn) {
        try {
            DescribeTasksRequest request = DescribeTasksRequest.builder()
                .cluster(clusterName)
                .tasks(taskArn)
                .build();
            
            DescribeTasksResponse response = ecsClient.describeTasks(request);
            return response.tasks().isEmpty() ? null : response.tasks().get(0);
        } catch (Exception e) {
            log.error("Failed to describe task: {}", taskArn, e);
            return null;
        }
    }

    private String extractPrivateIpFromTask(Task task) {
        if (task == null || task.attachments() == null || task.attachments().isEmpty()) {
            return null;
        }
        
        return task.attachments().stream()
            .filter(att -> "ElasticNetworkInterface".equals(att.type()))
            .flatMap(att -> att.details().stream())
            .filter(detail -> "privateIPv4Address".equals(detail.name()))
            .map(detail -> detail.value())
            .findFirst()
            .orElse(null);
    }
}
