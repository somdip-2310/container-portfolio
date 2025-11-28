package dev.somdip.containerplatform.repository;

import dev.somdip.containerplatform.model.GitHubConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import jakarta.annotation.PostConstruct;
import java.util.Optional;

@Repository
public class GitHubConnectionRepository {
    private static final Logger log = LoggerFactory.getLogger(GitHubConnectionRepository.class);

    private final DynamoDbClient dynamoDbClient;
    private final DynamoDbEnhancedClient enhancedClient;
    private DynamoDbTable<GitHubConnection> connectionTable;

    @Value("${aws.dynamodb.tables.github-connections:container-platform-github-connections}")
    private String tableName;

    public GitHubConnectionRepository(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
        this.enhancedClient = DynamoDbEnhancedClient.builder()
            .dynamoDbClient(dynamoDbClient)
            .build();
    }

    @PostConstruct
    public void init() {
        connectionTable = enhancedClient.table(tableName, TableSchema.fromBean(GitHubConnection.class));
        createTableIfNotExists();
    }

    private void createTableIfNotExists() {
        try {
            dynamoDbClient.describeTable(DescribeTableRequest.builder()
                .tableName(tableName)
                .build());
            log.info("GitHub connections table {} exists", tableName);
        } catch (ResourceNotFoundException e) {
            log.info("Creating GitHub connections table: {}", tableName);

            CreateTableRequest request = CreateTableRequest.builder()
                .tableName(tableName)
                .attributeDefinitions(
                    AttributeDefinition.builder()
                        .attributeName("connectionId")
                        .attributeType(ScalarAttributeType.S)
                        .build(),
                    AttributeDefinition.builder()
                        .attributeName("userId")
                        .attributeType(ScalarAttributeType.S)
                        .build()
                )
                .keySchema(
                    KeySchemaElement.builder()
                        .attributeName("connectionId")
                        .keyType(KeyType.HASH)
                        .build()
                )
                .globalSecondaryIndexes(
                    GlobalSecondaryIndex.builder()
                        .indexName("UserIdIndex")
                        .keySchema(
                            KeySchemaElement.builder()
                                .attributeName("userId")
                                .keyType(KeyType.HASH)
                                .build()
                        )
                        .projection(Projection.builder()
                            .projectionType(ProjectionType.ALL)
                            .build())
                        .provisionedThroughput(ProvisionedThroughput.builder()
                            .readCapacityUnits(5L)
                            .writeCapacityUnits(5L)
                            .build())
                        .build()
                )
                .provisionedThroughput(ProvisionedThroughput.builder()
                    .readCapacityUnits(5L)
                    .writeCapacityUnits(5L)
                    .build())
                .build();

            dynamoDbClient.createTable(request);
            log.info("Created GitHub connections table: {}", tableName);
        }
    }

    public GitHubConnection save(GitHubConnection connection) {
        connectionTable.putItem(connection);
        return connection;
    }

    public Optional<GitHubConnection> findById(String connectionId) {
        GitHubConnection connection = connectionTable.getItem(Key.builder()
            .partitionValue(connectionId)
            .build());
        return Optional.ofNullable(connection);
    }

    public Optional<GitHubConnection> findByUserId(String userId) {
        DynamoDbIndex<GitHubConnection> userIdIndex = connectionTable.index("UserIdIndex");

        QueryConditional queryConditional = QueryConditional.keyEqualTo(Key.builder()
            .partitionValue(userId)
            .build());

        return userIdIndex.query(QueryEnhancedRequest.builder()
            .queryConditional(queryConditional)
            .limit(1)
            .build())
            .stream()
            .flatMap(page -> page.items().stream())
            .findFirst();
    }

    public void delete(String connectionId) {
        connectionTable.deleteItem(Key.builder()
            .partitionValue(connectionId)
            .build());
    }

    public void deleteByUserId(String userId) {
        findByUserId(userId).ifPresent(connection -> delete(connection.getConnectionId()));
    }
}
