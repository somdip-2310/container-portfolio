package dev.somdip.containerplatform.service;

import dev.somdip.containerplatform.model.Container;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.*;
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class EcsService {
    private static final Logger log = LoggerFactory.getLogger(EcsService.class);
    
    private final EcsClient ecsClient;
    private final ElasticLoadBalancingV2Client elbClient;
    
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
    
    @Value("${aws.cloudwatch.logGroup.users}")
    private String logGroup;
    
    public EcsService(EcsClient ecsClient, ElasticLoadBalancingV2Client elbClient) {
        this.ecsClient = ecsClient;
        this.elbClient = elbClient;
    }
    
    public void deployContainer(Container container) {
        log.info("Deploying container {} to ECS", container.getContainerId());
        
        try {
            String taskDefinitionArn = createTaskDefinition(container);
            container.setTaskDefinitionArn(taskDefinitionArn);
            
            String serviceArn = createOrUpdateService(container);
            container.setServiceArn(serviceArn);
            
            log.info("Container deployed successfully: {}", container.getContainerId());
        } catch (Exception e) {
            log.error("Failed to deploy container: {}", container.getContainerId(), e);
            throw new RuntimeException("ECS deployment failed", e);
        }
    }
    
    private String createTaskDefinition(Container container) {
        String family = "container-" + container.getContainerId();
        
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
    
    private String createOrUpdateService(Container container) {
        String serviceName = "service-" + container.getContainerId();
        
        try {
            DescribeServicesRequest describeRequest = DescribeServicesRequest.builder()
                .cluster(clusterName)
                .services(serviceName)
                .build();
            
            DescribeServicesResponse describeResponse = ecsClient.describeServices(describeRequest);
            
            if (!describeResponse.services().isEmpty() && 
                describeResponse.services().get(0).status().equals("ACTIVE")) {
                UpdateServiceRequest updateRequest = UpdateServiceRequest.builder()
                    .cluster(clusterName)
                    .service(serviceName)
                    .taskDefinition(container.getTaskDefinitionArn())
                    .desiredCount(1)
                    .build();
                
                UpdateServiceResponse updateResponse = ecsClient.updateService(updateRequest);
                return updateResponse.service().serviceArn();
            }
        } catch (Exception e) {
            log.debug("Service does not exist, creating new one");
        }
        
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
                .targetGroupArn(targetGroupArn)
                .containerName(container.getContainerName())
                .containerPort(container.getPort())
                .build())
            .healthCheckGracePeriodSeconds(60)
            .build();
        
        CreateServiceResponse createResponse = ecsClient.createService(createRequest);
        return createResponse.service().serviceArn();
    }
    
    public void stopService(String serviceArn) {
        log.info("Stopping ECS service: {}", serviceArn);
        
        UpdateServiceRequest request = UpdateServiceRequest.builder()
            .cluster(clusterName)
            .service(serviceArn)
            .desiredCount(0)
            .build();
        
        ecsClient.updateService(request);
    }
    
    public void deleteService(String serviceArn) {
        log.info("Deleting ECS service: {}", serviceArn);
        
        stopService(serviceArn);
        
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
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