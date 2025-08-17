package dev.somdip.containerplatform.repository;

import dev.somdip.containerplatform.model.Deployment;
import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class DeploymentRepository {

	private static final Logger log = LoggerFactory.getLogger(DeploymentRepository.class);
    private final DynamoDbEnhancedClient enhancedClient;
    
    @Qualifier("deploymentsTableName")
    private final String tableName;

    private DynamoDbTable<Deployment> getTable() {
        return enhancedClient.table(tableName, Deployment.class);
    }

    public Deployment save(Deployment deployment) {
        if (deployment.getDeploymentId() == null) {
            deployment.setDeploymentId(UUID.randomUUID().toString());
        }
        
        log.debug("Saving deployment: {}", deployment.getDeploymentId());
        getTable().putItem(deployment);
        return deployment;
    }

    public Optional<Deployment> findById(String deploymentId) {
        log.debug("Finding deployment by ID: {}", deploymentId);
        Key key = Key.builder()
                .partitionValue(deploymentId)
                .build();
        
        Deployment deployment = getTable().getItem(key);
        return Optional.ofNullable(deployment);
    }

    public List<Deployment> findByContainerId(String containerId) {
        log.debug("Finding deployments by container ID: {}", containerId);
        DynamoDbIndex<Deployment> containerIdIndex = getTable().index("ContainerIdIndex");
        
        QueryConditional queryConditional = QueryConditional
                .keyEqualTo(Key.builder().partitionValue(containerId).build());
        
        QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .scanIndexForward(false) // Sort by newest first
                .build();
        
        return containerIdIndex.query(queryRequest)
                .items()
                .stream()
                .collect(Collectors.toList());
    }

    public List<Deployment> findByContainerIdWithLimit(String containerId, int limit) {
        log.debug("Finding {} recent deployments for container: {}", limit, containerId);
        DynamoDbIndex<Deployment> containerIdIndex = getTable().index("ContainerIdIndex");
        
        QueryConditional queryConditional = QueryConditional
                .keyEqualTo(Key.builder().partitionValue(containerId).build());
        
        QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .scanIndexForward(false) // Sort by newest first
                .limit(limit)
                .build();
        
        return containerIdIndex.query(queryRequest)
                .items()
                .stream()
                .collect(Collectors.toList());
    }

    public Optional<Deployment> findLatestByContainerId(String containerId) {
        List<Deployment> deployments = findByContainerIdWithLimit(containerId, 1);
        return deployments.isEmpty() ? Optional.empty() : Optional.of(deployments.get(0));
    }

    public List<Deployment> findByStatus(Deployment.DeploymentStatus status) {
        log.debug("Finding deployments by status: {}", status);
        
        // Note: This requires a scan since status is not indexed
        // In production, consider adding a GSI for status if this query is frequent
        return getTable().scan()
                .items()
                .stream()
                .filter(deployment -> deployment.getStatus() == status)
                .collect(Collectors.toList());
    }

    public List<Deployment> findActiveDeployments() {
        log.debug("Finding active deployments");
        
        return getTable().scan()
                .items()
                .stream()
                .filter(deployment -> 
                    deployment.getStatus() == Deployment.DeploymentStatus.PENDING ||
                    deployment.getStatus() == Deployment.DeploymentStatus.IN_PROGRESS)
                .collect(Collectors.toList());
    }

    public Deployment updateStatus(String deploymentId, Deployment.DeploymentStatus status) {
        Optional<Deployment> deploymentOpt = findById(deploymentId);
        if (deploymentOpt.isPresent()) {
            Deployment deployment = deploymentOpt.get();
            deployment.setStatus(status);
            if (status == Deployment.DeploymentStatus.COMPLETED || 
                status == Deployment.DeploymentStatus.FAILED ||
                status == Deployment.DeploymentStatus.ROLLED_BACK) {
                deployment.setCompletedAt(Instant.now());
                if (deployment.getStartedAt() != null) {
                    deployment.setDurationMillis(
                        Instant.now().toEpochMilli() - deployment.getStartedAt().toEpochMilli()
                    );
                }
            }
            return save(deployment);
        }
        throw new IllegalArgumentException("Deployment not found: " + deploymentId);
    }

    public Deployment addStep(String deploymentId, Deployment.DeploymentStep step) {
        Optional<Deployment> deploymentOpt = findById(deploymentId);
        if (deploymentOpt.isPresent()) {
            Deployment deployment = deploymentOpt.get();
            deployment.getSteps().add(step);
            return save(deployment);
        }
        throw new IllegalArgumentException("Deployment not found: " + deploymentId);
    }

    public void delete(String deploymentId) {
        log.debug("Deleting deployment: {}", deploymentId);
        Key key = Key.builder()
                .partitionValue(deploymentId)
                .build();
        
        getTable().deleteItem(key);
    }

    public long countByContainerId(String containerId) {
        return findByContainerId(containerId).size();
    }

    public List<Deployment> findByUserIdInTimeRange(String userId, Instant startTime, Instant endTime) {
        log.debug("Finding deployments for user {} between {} and {}", userId, startTime, endTime);
        
        // This requires a scan with filtering
        // In production, consider a compound key or GSI for userId+timestamp
        return getTable().scan()
                .items()
                .stream()
                .filter(deployment -> 
                    deployment.getUserId().equals(userId) &&
                    deployment.getStartedAt() != null &&
                    deployment.getStartedAt().isAfter(startTime) &&
                    deployment.getStartedAt().isBefore(endTime))
                .collect(Collectors.toList());
    }
}