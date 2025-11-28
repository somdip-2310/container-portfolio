package dev.somdip.containerplatform.repository;

import dev.somdip.containerplatform.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Repository
public class UserRepository {
    
    private static final Logger log = LoggerFactory.getLogger(UserRepository.class);

    private final DynamoDbEnhancedClient enhancedClient;
    private final String tableName;

    public UserRepository(DynamoDbEnhancedClient enhancedClient, 
                         @Qualifier("usersTableName") String tableName) {
        this.enhancedClient = enhancedClient;
        this.tableName = tableName;
    }

    private DynamoDbTable<User> getTable() {
        return enhancedClient.table(tableName, TableSchema.fromBean(User.class));
    }

    public User save(User user) {
        if (user.getUserId() == null) {
            user.setUserId(UUID.randomUUID().toString());
        }
        user.setUpdatedAt(Instant.now());
        if (user.getCreatedAt() == null) {
            user.setCreatedAt(Instant.now());
        }
        
        log.debug("Saving user: {}", user.getUserId());
        getTable().putItem(user);
        return user;
    }

    public Optional<User> findById(String userId) {
        log.debug("Finding user by ID: {}", userId);
        Key key = Key.builder()
                .partitionValue(userId)
                .build();
        
        User user = getTable().getItem(key);
        return Optional.ofNullable(user);
    }

    public Optional<User> findByEmail(String email) {
        log.debug("Finding user by email: {}", email);
        DynamoDbIndex<User> emailIndex = getTable().index("EmailIndex");
        
        QueryConditional queryConditional = QueryConditional
                .keyEqualTo(Key.builder().partitionValue(email).build());
        
        QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .limit(1)
                .build();
        
        return StreamSupport.stream(emailIndex.query(queryRequest).spliterator(), false)
                .flatMap(page -> page.items().stream())
                .findFirst();
    }

    public Optional<User> findByApiKey(String apiKey) {
        log.debug("Finding user by API key");
        DynamoDbIndex<User> apiKeyIndex = getTable().index("ApiKeyIndex");
        
        QueryConditional queryConditional = QueryConditional
                .keyEqualTo(Key.builder().partitionValue(apiKey).build());
        
        QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .limit(1)
                .build();
        
        return StreamSupport.stream(apiKeyIndex.query(queryRequest).spliterator(), false)
                .flatMap(page -> page.items().stream())
                .findFirst();
    }

    public void delete(String userId) {
        log.debug("Deleting user: {}", userId);
        Key key = Key.builder()
                .partitionValue(userId)
                .build();
        
        getTable().deleteItem(key);
    }

    public boolean existsByEmail(String email) {
        return findByEmail(email).isPresent();
    }

    public User updateLastLogin(String userId) {
        Optional<User> userOpt = findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setLastLoginAt(Instant.now());
            return save(user);
        }
        throw new IllegalArgumentException("User not found: " + userId);
    }

    public User updatePlan(String userId, User.UserPlan plan) {
        Optional<User> userOpt = findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setPlan(plan);
            return save(user);
        }
        throw new IllegalArgumentException("User not found: " + userId);
    }

    public User incrementContainerCount(String userId, int delta) {
        Optional<User> userOpt = findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            int currentCount = user.getContainerCount() != null ? user.getContainerCount() : 0;
            user.setContainerCount(currentCount + delta);
            return save(user);
        }
        throw new IllegalArgumentException("User not found: " + userId);
    }

    public List<User> findAll() {
        log.debug("Finding all users");
        return getTable().scan().items().stream().collect(Collectors.toList());
    }

    public List<User> findByPlan(User.UserPlan plan) {
        log.debug("Finding users by plan: {}", plan);

        // Use PlanIndex GSI for efficient lookup instead of table scan
        DynamoDbIndex<User> planIndex = getTable().index("PlanIndex");

        QueryConditional queryConditional = QueryConditional
                .keyEqualTo(Key.builder().partitionValue(plan.name()).build());

        QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .build();

        return StreamSupport.stream(planIndex.query(queryRequest).spliterator(), false)
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
    }
}