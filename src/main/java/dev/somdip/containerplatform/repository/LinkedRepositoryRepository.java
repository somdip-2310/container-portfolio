package dev.somdip.containerplatform.repository;

import dev.somdip.containerplatform.model.LinkedRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class LinkedRepositoryRepository {
    private static final Logger log = LoggerFactory.getLogger(LinkedRepositoryRepository.class);

    private final DynamoDbClient dynamoDbClient;
    private final DynamoDbEnhancedClient enhancedClient;
    private DynamoDbTable<LinkedRepository> repoTable;

    @Value("${aws.dynamodb.tables.linked-repositories:container-platform-linked-repositories}")
    private String tableName;

    public LinkedRepositoryRepository(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
        this.enhancedClient = DynamoDbEnhancedClient.builder()
            .dynamoDbClient(dynamoDbClient)
            .build();
    }

    @PostConstruct
    public void init() {
        repoTable = enhancedClient.table(tableName, TableSchema.fromBean(LinkedRepository.class));
        createTableIfNotExists();
    }

    private void createTableIfNotExists() {
        try {
            dynamoDbClient.describeTable(DescribeTableRequest.builder()
                .tableName(tableName)
                .build());
            log.info("Linked repositories table {} exists", tableName);
        } catch (ResourceNotFoundException e) {
            log.info("Creating linked repositories table: {}", tableName);

            CreateTableRequest request = CreateTableRequest.builder()
                .tableName(tableName)
                .attributeDefinitions(
                    AttributeDefinition.builder()
                        .attributeName("repoLinkId")
                        .attributeType(ScalarAttributeType.S)
                        .build(),
                    AttributeDefinition.builder()
                        .attributeName("userId")
                        .attributeType(ScalarAttributeType.S)
                        .build(),
                    AttributeDefinition.builder()
                        .attributeName("containerId")
                        .attributeType(ScalarAttributeType.S)
                        .build(),
                    AttributeDefinition.builder()
                        .attributeName("repoFullName")
                        .attributeType(ScalarAttributeType.S)
                        .build()
                )
                .keySchema(
                    KeySchemaElement.builder()
                        .attributeName("repoLinkId")
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
                        .build(),
                    GlobalSecondaryIndex.builder()
                        .indexName("ContainerIdIndex")
                        .keySchema(
                            KeySchemaElement.builder()
                                .attributeName("containerId")
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
                        .build(),
                    GlobalSecondaryIndex.builder()
                        .indexName("RepoFullNameIndex")
                        .keySchema(
                            KeySchemaElement.builder()
                                .attributeName("repoFullName")
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
            log.info("Created linked repositories table: {}", tableName);
        }
    }

    public LinkedRepository save(LinkedRepository repo) {
        repoTable.putItem(repo);
        return repo;
    }

    public Optional<LinkedRepository> findById(String repoLinkId) {
        LinkedRepository repo = repoTable.getItem(Key.builder()
            .partitionValue(repoLinkId)
            .build());
        return Optional.ofNullable(repo);
    }

    public List<LinkedRepository> findByUserId(String userId) {
        DynamoDbIndex<LinkedRepository> userIdIndex = repoTable.index("UserIdIndex");

        QueryConditional queryConditional = QueryConditional.keyEqualTo(Key.builder()
            .partitionValue(userId)
            .build());

        return userIdIndex.query(QueryEnhancedRequest.builder()
            .queryConditional(queryConditional)
            .build())
            .stream()
            .flatMap(page -> page.items().stream())
            .collect(Collectors.toList());
    }

    public Optional<LinkedRepository> findByContainerId(String containerId) {
        DynamoDbIndex<LinkedRepository> containerIdIndex = repoTable.index("ContainerIdIndex");

        QueryConditional queryConditional = QueryConditional.keyEqualTo(Key.builder()
            .partitionValue(containerId)
            .build());

        return containerIdIndex.query(QueryEnhancedRequest.builder()
            .queryConditional(queryConditional)
            .limit(1)
            .build())
            .stream()
            .flatMap(page -> page.items().stream())
            .findFirst();
    }

    public Optional<LinkedRepository> findByRepoFullName(String repoFullName) {
        DynamoDbIndex<LinkedRepository> repoFullNameIndex = repoTable.index("RepoFullNameIndex");

        QueryConditional queryConditional = QueryConditional.keyEqualTo(Key.builder()
            .partitionValue(repoFullName)
            .build());

        return repoFullNameIndex.query(QueryEnhancedRequest.builder()
            .queryConditional(queryConditional)
            .limit(1)
            .build())
            .stream()
            .flatMap(page -> page.items().stream())
            .findFirst();
    }

    public void delete(String repoLinkId) {
        repoTable.deleteItem(Key.builder()
            .partitionValue(repoLinkId)
            .build());
    }

    public void deleteByContainerId(String containerId) {
        findByContainerId(containerId).ifPresent(repo -> delete(repo.getRepoLinkId()));
    }
}
