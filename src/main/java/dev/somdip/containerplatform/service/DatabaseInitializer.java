package dev.somdip.containerplatform.service;

import dev.somdip.containerplatform.model.Container;
import dev.somdip.containerplatform.model.Deployment;
import dev.somdip.containerplatform.model.User;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.CreateTableEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.EnhancedGlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@Profile("!test") // Don't run in test environment
public class DatabaseInitializer {
    
    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    private final DynamoDbClient dynamoDbClient;
    private final DynamoDbEnhancedClient enhancedClient;
    private final String usersTableName;
    private final String containersTableName;
    private final String deploymentsTableName;

    @Value("${aws.dynamodb.initialize:true}")
    private boolean initializeTables;

    public DatabaseInitializer(DynamoDbClient dynamoDbClient, 
                              DynamoDbEnhancedClient enhancedClient,
                              @Qualifier("usersTableName") String usersTableName, 
                              @Qualifier("containersTableName") String containersTableName, 
                              @Qualifier("deploymentsTableName") String deploymentsTableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.enhancedClient = enhancedClient;
        this.usersTableName = usersTableName;
        this.containersTableName = containersTableName;
        this.deploymentsTableName = deploymentsTableName;
    }

    @PostConstruct
    public void initialize() {
        if (!initializeTables) {
            log.info("Database initialization is disabled");
            return;
        }

        log.info("Initializing DynamoDB tables...");
        
        CompletableFuture<Void> usersFuture = CompletableFuture.runAsync(() -> createUsersTable());
        CompletableFuture<Void> containersFuture = CompletableFuture.runAsync(() -> createContainersTable());
        CompletableFuture<Void> deploymentsFuture = CompletableFuture.runAsync(() -> createDeploymentsTable());
        
        CompletableFuture.allOf(usersFuture, containersFuture, deploymentsFuture).join();
        
        log.info("DynamoDB tables initialization completed");
    }

    private void createUsersTable() {
        if (tableExists(usersTableName)) {
            log.info("Table {} already exists", usersTableName);
            return;
        }

        log.info("Creating table: {}", usersTableName);
        
        DynamoDbTable<User> table = enhancedClient.table(usersTableName, TableSchema.fromBean(User.class));
        
        CreateTableEnhancedRequest createTableRequest = CreateTableEnhancedRequest.builder()
                .globalSecondaryIndices(
                    EnhancedGlobalSecondaryIndex.builder()
                        .indexName("EmailIndex")
                        .projection(projection -> projection.projectionType(ProjectionType.ALL))
                        .provisionedThroughput(throughput -> throughput
                            .readCapacityUnits(5L)
                            .writeCapacityUnits(5L))
                        .build(),
                    EnhancedGlobalSecondaryIndex.builder()
                        .indexName("ApiKeyIndex")
                        .projection(projection -> projection.projectionType(ProjectionType.ALL))
                        .provisionedThroughput(throughput -> throughput
                            .readCapacityUnits(5L)
                            .writeCapacityUnits(5L))
                        .build()
                )
                .provisionedThroughput(throughput -> throughput
                    .readCapacityUnits(5L)
                    .writeCapacityUnits(5L))
                .build();

        try {
            table.createTable(createTableRequest);
            waitForTableCreation(usersTableName);
            
            // Update to on-demand billing after creation
            updateTableBillingMode(usersTableName);
            
            log.info("Table {} created successfully", usersTableName);
        } catch (Exception e) {
            log.error("Error creating table {}: {}", usersTableName, e.getMessage());
            throw new RuntimeException("Failed to create users table", e);
        }
    }

    private void createContainersTable() {
        if (tableExists(containersTableName)) {
            log.info("Table {} already exists", containersTableName);
            return;
        }

        log.info("Creating table: {}", containersTableName);
        
        DynamoDbTable<Container> table = enhancedClient.table(containersTableName, TableSchema.fromBean(Container.class));
        
        CreateTableEnhancedRequest createTableRequest = CreateTableEnhancedRequest.builder()
                .globalSecondaryIndices(
                    EnhancedGlobalSecondaryIndex.builder()
                        .indexName("UserIdIndex")
                        .projection(projection -> projection.projectionType(ProjectionType.ALL))
                        .provisionedThroughput(throughput -> throughput
                            .readCapacityUnits(5L)
                            .writeCapacityUnits(5L))
                        .build()
                )
                .provisionedThroughput(throughput -> throughput
                    .readCapacityUnits(5L)
                    .writeCapacityUnits(5L))
                .build();

        try {
            table.createTable(createTableRequest);
            waitForTableCreation(containersTableName);
            
            // Update to on-demand billing after creation
            updateTableBillingMode(containersTableName);
            
            log.info("Table {} created successfully", containersTableName);
        } catch (Exception e) {
            log.error("Error creating table {}: {}", containersTableName, e.getMessage());
            throw new RuntimeException("Failed to create containers table", e);
        }
    }

    private void createDeploymentsTable() {
        if (tableExists(deploymentsTableName)) {
            log.info("Table {} already exists", deploymentsTableName);
            return;
        }

        log.info("Creating table: {}", deploymentsTableName);
        
        DynamoDbTable<Deployment> table = enhancedClient.table(deploymentsTableName, TableSchema.fromBean(Deployment.class));
        
        CreateTableEnhancedRequest createTableRequest = CreateTableEnhancedRequest.builder()
                .globalSecondaryIndices(
                    EnhancedGlobalSecondaryIndex.builder()
                        .indexName("ContainerIdIndex")
                        .projection(projection -> projection.projectionType(ProjectionType.ALL))
                        .provisionedThroughput(throughput -> throughput
                            .readCapacityUnits(5L)
                            .writeCapacityUnits(5L))
                        .build()
                )
                .provisionedThroughput(throughput -> throughput
                    .readCapacityUnits(5L)
                    .writeCapacityUnits(5L))
                .build();

        try {
            table.createTable(createTableRequest);
            waitForTableCreation(deploymentsTableName);
            
            // Update to on-demand billing after creation
            updateTableBillingMode(deploymentsTableName);
            
            log.info("Table {} created successfully", deploymentsTableName);
        } catch (Exception e) {
            log.error("Error creating table {}: {}", deploymentsTableName, e.getMessage());
            throw new RuntimeException("Failed to create deployments table", e);
        }
    }

    private boolean tableExists(String tableName) {
        try {
            DescribeTableRequest request = DescribeTableRequest.builder()
                    .tableName(tableName)
                    .build();
            
            dynamoDbClient.describeTable(request);
            return true;
        } catch (ResourceNotFoundException e) {
            return false;
        }
    }

    private void waitForTableCreation(String tableName) {
        log.info("Waiting for table {} to become active...", tableName);
        
        dynamoDbClient.waiter().waitUntilTableExists(
            DescribeTableRequest.builder()
                    .tableName(tableName)
                    .build()
        );
        
        log.info("Table {} is now active", tableName);
    }

    private void updateTableBillingMode(String tableName) {
        try {
            log.info("Updating table {} to on-demand billing mode", tableName);
            
            UpdateTableRequest updateRequest = UpdateTableRequest.builder()
                    .tableName(tableName)
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .build();
            
            dynamoDbClient.updateTable(updateRequest);
            
            // Wait for update to complete
            dynamoDbClient.waiter().waitUntilTableExists(
                DescribeTableRequest.builder()
                        .tableName(tableName)
                        .build()
            );
            
            log.info("Table {} updated to on-demand billing mode", tableName);
        } catch (Exception e) {
            log.error("Error updating billing mode for table {}: {}", tableName, e.getMessage());
            // Non-critical error, continue
        }
    }
}