package dev.somdip.containerplatform.service;

import dev.somdip.containerplatform.model.Container;
import dev.somdip.containerplatform.model.Deployment;
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
    
    @Value("${aws.cloudwatch.logGroup.users}")
    private String logGroup;
    
    public EcsService(EcsClient ecsClient, 
                     ElasticLoadBalancingV2Client elbClient,
                     TargetGroupService targetGroupService,
                     DeploymentRepository deploymentRepository) {
        this.ecsClient = ecsClient;
        this.elbClient = elbClient;
        this.targetGroupService = targetGroupService;
        this.deploymentRepository = deploymentRepository;
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
            
            // Step 5: Register with target group
            updateDeploymentStep(deployment, "REGISTER_TARGET_GROUP", Deployment.DeploymentStep.StepStatus.IN_PROGRESS);
            targetGroupService.registerTaskWithTargetGroup(userContainersTargetGroupArn, taskArn);
            container.setTargetGroupArn(targetGroupArn);
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
        deployment.setUserId(container.getUserId());
        deployment.setInitiatedBy(userId);
        deployment.setType(Deployment.DeploymentType.CREATE);
        deployment.setStatus(Deployment.DeploymentStatus.IN_PROGRESS);
        deployment.setStartedAt(Instant.now());
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
        log.info("Waiting for service to stabilize: {}", serviceArn);
        
        int maxAttempts = 30;
        int attempt = 0;
        
        while (attempt < maxAttempts) {
            DescribeServicesRequest request = DescribeServicesRequest.builder()
                .cluster(clusterName)
                .services(serviceArn)
                .build();
            
            DescribeServicesResponse response = ecsClient.describeServices(request);
            
            if (!response.services().isEmpty()) {
                software.amazon.awssdk.services.ecs.model.Service service = response.services().get(0);
                
                if (service.runningCount() == service.desiredCount() &&
                    service.deployments().size() == 1) {
                    log.info("Service is stable with {} running tasks", service.runningCount());
                    return;
                }
                
                log.debug("Service not stable yet. Running: {}, Desired: {}, Deployments: {}",
                    service.runningCount(), service.desiredCount(), service.deployments().size());
            }
            
            attempt++;
            Thread.sleep(10000); // Wait 10 seconds
        }
        
        throw new RuntimeException("Service did not stabilize within timeout");
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
        
        // Build container definition
        ContainerDefinition containerDef = ContainerDefinition.builder()
            .name(container.getContainerName())
            .image(container.getImage() + ":" + container.getImageTag())
            .cpu(container.getCpu())
            .memory(container.getMemory())
            .essential(true)
            .portMappings(PortMapping.builder()
                .containerPort(container.getPort())
                .protocol("tcp")
                .build())
            .environment(convertEnvironmentVariables(container.getEnvironmentVariables()))
            .logConfiguration(LogConfiguration.builder()
                .logDriver("awslogs")
                .options(Map.of(
                    "awslogs-group", logGroup,
                    "awslogs-region", "us-east-1",
                    "awslogs-stream-prefix", container.getContainerId()
                ))
                .build())
            .healthCheck(createHealthCheckConfig(container))
            .build();
        
        RegisterTaskDefinitionRequest request = RegisterTaskDefinitionRequest.builder()
            .family(family)
            .taskRoleArn(taskRoleArn)
            .executionRoleArn(executionRoleArn)
            .networkMode(NetworkMode.AWSVPC)
            .requiresCompatibilities(Compatibility.FARGATE)
            .cpu(String.valueOf(container.getCpu()))
            .memory(String.valueOf(container.getMemory()))
            .containerDefinitions(containerDef)
            .build();
        
        RegisterTaskDefinitionResponse response = ecsClient.registerTaskDefinition(request);
        return response.taskDefinition().taskDefinitionArn();
    }
    
    private HealthCheck createHealthCheckConfig(Container container) {
        if (container.getHealthCheck() == null) {
            // Default health check
            return HealthCheck.builder()
                .command("CMD-SHELL", "curl -f http://localhost:" + container.getPort() + "/health || exit 1")
                .interval(30)
                .timeout(5)
                .retries(3)
                .startPeriod(60)
                .build();
        }
        
        Container.HealthCheckConfig hc = container.getHealthCheck();
        String command = String.format("CMD-SHELL, curl -f http://localhost:%d%s || exit 1",
            container.getPort(), hc.getPath() != null ? hc.getPath() : "/health");
        
        return HealthCheck.builder()
            .command(command)
            .interval(hc.getInterval() != null ? hc.getInterval() : 30)
            .timeout(hc.getTimeout() != null ? hc.getTimeout() : 5)
            .retries(hc.getHealthyThreshold() != null ? hc.getHealthyThreshold() : 3)
            .startPeriod(60)
            .build();
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
        log.info("Stopping ECS service: {}", serviceArn);
        
        // First deregister from target group
        try {
            String taskArn = getRunningTaskArn(serviceArn);
            targetGroupService.deregisterTaskFromTargetGroup(targetGroupArn, taskArn);
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
    }
    
    public void deleteService(String serviceArn, String containerId) {
        log.info("Deleting ECS service: {}", serviceArn);
        
        // Stop service first
        stopService(serviceArn, containerId);
        
        // Wait for tasks to stop
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Delete service
        DeleteServiceRequest request = DeleteServiceRequest.builder()
            .cluster(clusterName)
            .service(serviceArn)
            .force(true)
            .build();
        
        ecsClient.deleteService(request);
    }
    
    private Collection<KeyValuePair> convertEnvironmentVariables(Map<String, String> envVars) {
        if (envVars == null || envVars.isEmpty()) {
            return Collections.emptyList();
        }
        
        return envVars.entrySet().stream()
            .map(entry -> KeyValuePair.builder()
                .name(entry.getKey())
                .value(entry.getValue())
                .build())
            .collect(Collectors.toList());
    }
}