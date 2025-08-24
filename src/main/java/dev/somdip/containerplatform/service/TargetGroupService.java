package dev.somdip.containerplatform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.*;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeNetworkInterfacesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeNetworkInterfacesResponse;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.DescribeTasksRequest;
import software.amazon.awssdk.services.ecs.model.DescribeTasksResponse;
import software.amazon.awssdk.services.ecs.model.Task;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class TargetGroupService {
    private static final Logger log = LoggerFactory.getLogger(TargetGroupService.class);
    
    private final ElasticLoadBalancingV2Client elbClient;
    private final EcsClient ecsClient;
    private final Ec2Client ec2Client;
    
    @Value("${aws.ecs.cluster}")
    private String clusterName;
    
    @Value("${aws.alb.targetGroup.arn}")
    private String defaultTargetGroupArn;
    
    public TargetGroupService(ElasticLoadBalancingV2Client elbClient, 
                            EcsClient ecsClient,
                            Ec2Client ec2Client) {
        this.elbClient = elbClient;
        this.ecsClient = ecsClient;
        this.ec2Client = ec2Client;
    }
    
    /**
     * Creates a new target group for a container
     */
    public String createTargetGroup(String containerName, Integer containerPort) {
        log.info("Creating target group for container: {} on port: {}", containerName, containerPort);
        
        try {
            String targetGroupName = "tg-" + containerName.substring(0, Math.min(containerName.length(), 28));
            
            CreateTargetGroupRequest request = CreateTargetGroupRequest.builder()
                .name(targetGroupName)
                .protocol(ProtocolEnum.HTTP)
                .port(containerPort)
                .vpcId("vpc-0e30d5748ef8afccf") // Your VPC ID
                .healthCheckEnabled(true)
                .healthCheckPath("/health")
                .healthCheckProtocol(ProtocolEnum.HTTP)
                .healthCheckIntervalSeconds(30)
                .healthCheckTimeoutSeconds(5)
                .healthyThresholdCount(2)
                .unhealthyThresholdCount(3)
                .targetType(TargetTypeEnum.IP)
                .tags(Tag.builder()
                    .key("Name")
                    .value(targetGroupName)
                    .build(),
                    Tag.builder()
                    .key("Container")
                    .value(containerName)
                    .build())
                .build();
            
            CreateTargetGroupResponse response = elbClient.createTargetGroup(request);
            String targetGroupArn = response.targetGroups().get(0).targetGroupArn();
            
            log.info("Created target group: {}", targetGroupArn);
            return targetGroupArn;
            
        } catch (Exception e) {
            log.error("Failed to create target group for container: {}", containerName, e);
            throw new RuntimeException("Failed to create target group", e);
        }
    }
    
    /**
     * Registers ECS task IPs with the target group
     */
    public void registerTaskWithTargetGroup(String targetGroupArn, String taskArn, Integer port) {
        log.info("Registering task {} with target group {} on port {}", taskArn, targetGroupArn, port);
        
        try {
            // Wait for task to be running
            waitForTaskRunning(taskArn);
            
            // Get task details
            Task task = getTaskDetails(taskArn);
            if (task == null) {
                throw new RuntimeException("Task not found: " + taskArn);
            }
            
            // Extract ENI (Elastic Network Interface) from task
            String eniId = extractEniFromTask(task);
            if (eniId == null) {
                throw new RuntimeException("No ENI found for task: " + taskArn);
            }
            
            // Get private IP from ENI
            String privateIp = getPrivateIpFromEni(eniId);
            if (privateIp == null) {
                throw new RuntimeException("No private IP found for ENI: " + eniId);
            }
            
            // Register the IP with target group
            registerTargets(targetGroupArn, privateIp, port);
            
            // Wait for target to become healthy
            waitForTargetHealthy(targetGroupArn, privateIp,port);
            
        } catch (Exception e) {
            log.error("Failed to register task with target group", e);
            throw new RuntimeException("Failed to register task", e);
        }
    }
    
    /**
     * Deregisters targets from target group
     */
    public void deregisterTaskFromTargetGroup(String targetGroupArn, String taskArn, Integer port) {
        log.info("Deregistering task {} from target group {} on port {}", taskArn, targetGroupArn, port);
        
        try {
            Task task = getTaskDetails(taskArn);
            if (task == null) {
                log.warn("Task not found, skipping deregistration: {}", taskArn);
                return;
            }
            
            String eniId = extractEniFromTask(task);
            if (eniId != null) {
                String privateIp = getPrivateIpFromEni(eniId);
                if (privateIp != null) {
                    deregisterTargets(targetGroupArn, privateIp, port);
                }
            }
        } catch (Exception e) {
            log.error("Error deregistering task from target group", e);
        }
    }
    
    /**
     * Deletes a target group
     */
    public void deleteTargetGroup(String targetGroupArn) {
        log.info("Deleting target group: {}", targetGroupArn);
        
        try {
            DeleteTargetGroupRequest request = DeleteTargetGroupRequest.builder()
                .targetGroupArn(targetGroupArn)
                .build();
            
            elbClient.deleteTargetGroup(request);
            log.info("Deleted target group: {}", targetGroupArn);
            
        } catch (Exception e) {
            log.error("Failed to delete target group: {}", targetGroupArn, e);
        }
    }
    
    private void waitForTaskRunning(String taskArn) throws InterruptedException {
        int maxAttempts = 30;
        int attempt = 0;
        
        while (attempt < maxAttempts) {
            Task task = getTaskDetails(taskArn);
            if (task != null && "RUNNING".equals(task.lastStatus())) {
                log.info("Task is running: {}", taskArn);
                return;
            }
            
            attempt++;
            log.debug("Waiting for task to be running, attempt {}/{}", attempt, maxAttempts);
            TimeUnit.SECONDS.sleep(10);
        }
        
        throw new RuntimeException("Task did not reach RUNNING state: " + taskArn);
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
    
    private String extractEniFromTask(Task task) {
        if (task.attachments() == null || task.attachments().isEmpty()) {
            return null;
        }
        
        return task.attachments().stream()
            .filter(att -> "ElasticNetworkInterface".equals(att.type()))
            .flatMap(att -> att.details().stream())
            .filter(detail -> "networkInterfaceId".equals(detail.name()))
            .map(detail -> detail.value())
            .findFirst()
            .orElse(null);
    }
    
    private String getPrivateIpFromEni(String eniId) {
        try {
            DescribeNetworkInterfacesRequest request = DescribeNetworkInterfacesRequest.builder()
                .networkInterfaceIds(eniId)
                .build();
            
            DescribeNetworkInterfacesResponse response = ec2Client.describeNetworkInterfaces(request);
            
            if (!response.networkInterfaces().isEmpty()) {
                return response.networkInterfaces().get(0).privateIpAddress();
            }
            
        } catch (Exception e) {
            log.error("Failed to get private IP for ENI: {}", eniId, e);
        }
        
        return null;
    }
    
    private void registerTargets(String targetGroupArn, String privateIp, Integer port) {
        RegisterTargetsRequest request = RegisterTargetsRequest.builder()
            .targetGroupArn(targetGroupArn)
            .targets(TargetDescription.builder()
                .id(privateIp)
                .port(port != null ? port : 8080) // Use actual port
                .build())
            .build();
        
        elbClient.registerTargets(request);
        log.info("Registered IP {} with target group", privateIp);
    }
    
    private void deregisterTargets(String targetGroupArn, String privateIp, Integer port) {
        DeregisterTargetsRequest request = DeregisterTargetsRequest.builder()
            .targetGroupArn(targetGroupArn)
            .targets(TargetDescription.builder()
                .id(privateIp)
                .port(port) //port for deregistration
                .build())
            .build();
        
        elbClient.deregisterTargets(request);
        log.info("Deregistered IP {} from target group", privateIp);
    }
    
    private void waitForTargetHealthy(String targetGroupArn, String privateIp, Integer port) throws InterruptedException {
        int maxAttempts = 30;
        int attempt = 0;
        
        while (attempt < maxAttempts) {
            DescribeTargetHealthRequest request = DescribeTargetHealthRequest.builder()
                .targetGroupArn(targetGroupArn)
                .targets(TargetDescription.builder()
                    .id(privateIp)
                    .port(port)
                    .build())
                .build();
            
            DescribeTargetHealthResponse response = elbClient.describeTargetHealth(request);
            
            if (!response.targetHealthDescriptions().isEmpty()) {
                String state = response.targetHealthDescriptions().get(0).targetHealth().stateAsString();
                if ("healthy".equalsIgnoreCase(state)) {
                    log.info("Target is healthy: {}", privateIp);
                    return;
                }
                log.debug("Target state: {}", state);
            }
            
            attempt++;
            TimeUnit.SECONDS.sleep(10);
        }
        
        log.warn("Target did not become healthy within timeout: {}", privateIp);
    }
}