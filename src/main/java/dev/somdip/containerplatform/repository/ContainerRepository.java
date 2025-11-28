package dev.somdip.containerplatform.repository;

import dev.somdip.containerplatform.model.Container;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Repository
public class ContainerRepository {

    private static final Logger log = LoggerFactory.getLogger(ContainerRepository.class);

    private final DynamoDbEnhancedClient enhancedClient;
    private final String tableName;

    public ContainerRepository(DynamoDbEnhancedClient enhancedClient,
                              @Qualifier("containersTableName") String tableName) {
        this.enhancedClient = enhancedClient;
        this.tableName = tableName;
    }

    private DynamoDbTable<Container> getTable() {
        return enhancedClient.table(tableName, TableSchema.fromBean(Container.class));
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
        
        return StreamSupport.stream(userIdIndex.query(queryRequest).spliterator(), false)
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
    }

    public List<Container> findByUserIdAndStatus(String userId, Container.ContainerStatus status) {
        return findByUserId(userId).stream()
                .filter(container -> container.getStatus() == status)
                .collect(Collectors.toList());
    }

    public Optional<Container> findBySubdomain(String subdomain) {
        log.debug("Finding container by subdomain: {}", subdomain);

        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":subdomain", AttributeValue.builder().s(subdomain).build());
        expressionValues.put(":deletedStatus", AttributeValue.builder().s("DELETED").build());

        Expression filterExpression = Expression.builder()
                .expression("subdomain = :subdomain AND containerStatus <> :deletedStatus")
                .expressionValues(expressionValues)
                .build();

        ScanEnhancedRequest scanRequest = ScanEnhancedRequest.builder()
                .filterExpression(filterExpression)
                .build();

        return StreamSupport.stream(getTable().scan(scanRequest).spliterator(), false)
                .flatMap(page -> page.items().stream())
                .findFirst();
    }

    public Optional<Container> findByCustomDomain(String customDomain) {
        log.debug("Finding container by custom domain: {}", customDomain);
        
        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":customDomain", AttributeValue.builder().s(customDomain).build());
        
        Expression filterExpression = Expression.builder()
                .expression("customDomain = :customDomain")
                .expressionValues(expressionValues)
                .build();
        
        ScanEnhancedRequest scanRequest = ScanEnhancedRequest.builder()
                .filterExpression(filterExpression)
                .build();
        
        return StreamSupport.stream(getTable().scan(scanRequest).spliterator(), false)
                .flatMap(page -> page.items().stream())
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
        return StreamSupport.stream(getTable().scan().spliterator(), false)
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
    }

    public long countByUserId(String userId) {
        return findByUserId(userId).size();
    }

    public long countActiveByUserId(String userId) {
        return findByUserIdAndStatus(userId, Container.ContainerStatus.RUNNING).size();
    }
    
    public long countNonDeletedByUserId(String userId) {
        // Since containers in DELETING status are being deleted, we shouldn't count them
        return findByUserId(userId).stream()
                .filter(container -> 
                    container.getStatus() != Container.ContainerStatus.DELETING)
                .count();
    }
}