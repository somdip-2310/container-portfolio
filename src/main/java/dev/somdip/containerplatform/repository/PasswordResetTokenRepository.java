package dev.somdip.containerplatform.repository;

import dev.somdip.containerplatform.model.PasswordResetToken;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.util.Optional;

@Repository
public class PasswordResetTokenRepository {
    private final DynamoDbTable<PasswordResetToken> table;

    public PasswordResetTokenRepository(DynamoDbEnhancedClient dynamoDbClient) {
        this.table = dynamoDbClient.table("password-reset-tokens", TableSchema.fromBean(PasswordResetToken.class));
    }

    public void save(PasswordResetToken token) {
        table.putItem(token);
    }

    public Optional<PasswordResetToken> findByEmail(String email) {
        Key key = Key.builder().partitionValue(email).build();
        PasswordResetToken token = table.getItem(key);
        return Optional.ofNullable(token);
    }

    public void delete(String email) {
        Key key = Key.builder().partitionValue(email).build();
        table.deleteItem(key);
    }
}
