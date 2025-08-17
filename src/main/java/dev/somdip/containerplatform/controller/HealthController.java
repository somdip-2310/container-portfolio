package dev.somdip.containerplatform.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.somdip.containerplatform.repository.ContainerRepository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ListTablesRequest;
import software.amazon.awssdk.services.dynamodb.model.ListTablesResponse;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.DescribeClustersRequest;
import software.amazon.awssdk.services.ecs.model.DescribeClustersResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/health")
@RequiredArgsConstructor

public class HealthController {
	
	private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final DynamoDbClient dynamoDbClient;
    private final EcsClient ecsClient;
    private final S3Client s3Client;

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${aws.region}")
    private String awsRegion;

    @Value("${aws.ecs.cluster}")
    private String ecsCluster;

    @Value("${aws.s3.bucket.logs}")
    private String s3LogsBucket;

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("application", applicationName);
        health.put("timestamp", Instant.now().toString());
        health.put("region", awsRegion);
        
        return ResponseEntity.ok(health);
    }

    @GetMapping("/detailed")
    public ResponseEntity<Map<String, Object>> detailedHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("application", applicationName);
        health.put("timestamp", Instant.now().toString());
        health.put("region", awsRegion);
        
        Map<String, Object> services = new HashMap<>();
        
        // Check DynamoDB
        services.put("dynamodb", checkDynamoDB());
        
        // Check ECS
        services.put("ecs", checkECS());
        
        // Check S3
        services.put("s3", checkS3());
        
        health.put("services", services);
        
        // Overall status
        boolean allHealthy = services.values().stream()
                .allMatch(service -> "UP".equals(((Map<?, ?>) service).get("status")));
        
        health.put("status", allHealthy ? "UP" : "DEGRADED");
        
        return ResponseEntity.ok(health);
    }

    private Map<String, Object> checkDynamoDB() {
        Map<String, Object> dynamoHealth = new HashMap<>();
        try {
            ListTablesRequest request = ListTablesRequest.builder()
                    .limit(1)
                    .build();
            
            ListTablesResponse response = dynamoDbClient.listTables(request);
            dynamoHealth.put("status", "UP");
            dynamoHealth.put("tableCount", response.tableNames().size());
            log.debug("DynamoDB health check passed");
        } catch (Exception e) {
            log.error("DynamoDB health check failed", e);
            dynamoHealth.put("status", "DOWN");
            dynamoHealth.put("error", e.getMessage());
        }
        return dynamoHealth;
    }

    private Map<String, Object> checkECS() {
        Map<String, Object> ecsHealth = new HashMap<>();
        try {
            DescribeClustersRequest request = DescribeClustersRequest.builder()
                    .clusters(ecsCluster)
                    .build();
            
            DescribeClustersResponse response = ecsClient.describeClusters(request);
            if (!response.clusters().isEmpty()) {
                ecsHealth.put("status", "UP");
                ecsHealth.put("clusterName", response.clusters().get(0).clusterName());
                ecsHealth.put("clusterStatus", response.clusters().get(0).status());
                log.debug("ECS health check passed");
            } else {
                ecsHealth.put("status", "DOWN");
                ecsHealth.put("error", "Cluster not found");
            }
        } catch (Exception e) {
            log.error("ECS health check failed", e);
            ecsHealth.put("status", "DOWN");
            ecsHealth.put("error", e.getMessage());
        }
        return ecsHealth;
    }

    private Map<String, Object> checkS3() {
        Map<String, Object> s3Health = new HashMap<>();
        try {
            HeadBucketRequest request = HeadBucketRequest.builder()
                    .bucket(s3LogsBucket)
                    .build();
            
            s3Client.headBucket(request);
            s3Health.put("status", "UP");
            s3Health.put("bucket", s3LogsBucket);
            log.debug("S3 health check passed");
        } catch (Exception e) {
            log.error("S3 health check failed", e);
            s3Health.put("status", "DOWN");
            s3Health.put("error", e.getMessage());
        }
        return s3Health;
    }
}