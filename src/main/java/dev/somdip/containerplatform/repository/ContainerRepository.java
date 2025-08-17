package dev.somdip.containerplatform.repository;

import dev.somdip.containerplatform.config.DynamoDbConfig;
import dev.somdip.containerplatform.model.Container;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor

public class ContainerRepository {
	
	private static final Logger log = LoggerFactory.getLogger(ContainerRepository.class);

    private final DynamoDbEnhancedClient enhancedClient;
    
    @Qualifier("containersTableName")
    private final String tableName;

    private DynamoDbTable<Container> getTable() {
        return enhancedClient.table(tableName, Container.class);
    }

    public Container save(Container container) {
        if (container.getContainerId() == null) {
            container.setContainerId(UUID.randomUUID().toString());
        }
        container.setUpdatedAt(Instant.now());
        if (container.getCreatedAt() == null) {
            container.setCreatedAt(Instant.now());
        }
        
        log.debug("Saving container: {}", container.getContainerId());
        getTable().putItem(container);
        return container;
    }

    public Optional<Container> findById(String containerId) {
        log.debug("Finding container by ID: {}", containerId);
        Key key = Key.builder()
                .partitionValue(containerId)
                .build();
        
        Container container = getTable().getItem(key);
        return Optional.ofNullable(container);
    }

    public List<Container> findByUserId(String userId) {
        log.debug("Finding containers by user ID: {}", userId);
        DynamoDbIndex<Container> userIdIndex = getTable().index("UserIdIndex");
        
        QueryConditional queryConditional = QueryConditional
                .keyEqualTo(Key.builder().partitionValue(userId).build());
        
        QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .build();
        
        return userIdIndex.query(queryRequest)
                .items()
                .stream()
                .collect(Collectors.toList());
    }

    public List<Container> findByUserIdAndStatus(String userId, Container.ContainerStatus status) {
        return findByUserId(userId).stream()
                .filter(container -> container.getStatus() == status)
                .collect(Collectors.toList());
    }

    public Optional<Container> findBySubdomain(String subdomain) {
        log.debug("Finding container by subdomain: {}", subdomain);
        
        // Since subdomain is not indexed, we need to scan
        // In production, consider adding a GSI for subdomain
        ScanEnhancedRequest scanRequest = ScanEnhancedRequest.builder()
                .filterExpression(expression -> expression
                        .attributeEquals("subdomain", subdomain))
                .build();
        
        return getTable().scan(scanRequest)
                .items()
                .stream()
                .findFirst();
    }

    public Optional<Container> findByCustomDomain(String customDomain) {
        log.debug("Finding container by custom domain: {}", customDomain);
        
        // Since customDomain is not indexed, we need to scan
        // In production, consider adding a GSI for customDomain
        ScanEnhancedRequest scanRequest = ScanEnhancedRequest.builder()
                .filterExpression(expression -> expression
                        .attributeEquals("customDomain", customDomain))
                .build();
        
        return getTable().scan(scanRequest)
                .items()
                .stream()
                .findFirst();
    }

    public void delete(String containerId) {
        log.debug("Deleting container: {}", containerId);
        Key key = Key.builder()
                .partitionValue(containerId)
                .build();
        
        getTable().deleteItem(key);
    }

    public Container updateStatus(String containerId, Container.ContainerStatus status) {
        Optional<Container> containerOpt = findById(containerId);
        if (containerOpt.isPresent()) {
            Container container = containerOpt.get();
            container.setStatus(status);
            return save(container);
        }
        throw new IllegalArgumentException("Container not found: " + containerId);
    }

    public Container updateTaskArns(String containerId, String taskDefinitionArn, 
                                   String serviceArn, String taskArn) {
        Optional<Container> containerOpt = findById(containerId);
        if (containerOpt.isPresent()) {
            Container container = containerOpt.get();
            container.setTaskDefinitionArn(taskDefinitionArn);
            container.setServiceArn(serviceArn);
            container.setTaskArn(taskArn);
            container.setLastDeployedAt(Instant.now());
            Long deploymentCount = container.getDeploymentCount() != null ? 
                    container.getDeploymentCount() : 0L;
            container.setDeploymentCount(deploymentCount + 1);
            return save(container);
        }
        throw new IllegalArgumentException("Container not found: " + containerId);
    }

    public List<Container> findAll() {
        log.debug("Finding all containers");
        return getTable().scan().items().stream().collect(Collectors.toList());
    }

    public long countByUserId(String userId) {
        return findByUserId(userId).size();
    }

    public long countActiveByUserId(String userId) {
        return findByUserIdAndStatus(userId, Container.ContainerStatus.RUNNING).size();
    }
}