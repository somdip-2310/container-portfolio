package dev.somdip.containerplatform.repository;

import dev.somdip.containerplatform.model.PaymentMethod;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class PaymentMethodRepository {

    private final DynamoDbTable<PaymentMethod> table;
    private final DynamoDbIndex<PaymentMethod> userIdIndex;

    public PaymentMethodRepository(DynamoDbEnhancedClient enhancedClient,
                                   @Qualifier("paymentMethodsTableName") String tableName) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(PaymentMethod.class));
        this.userIdIndex = table.index("UserIdIndex");
    }

    public PaymentMethod save(PaymentMethod paymentMethod) {
        if (paymentMethod.getPaymentMethodId() == null) {
            paymentMethod.setPaymentMethodId(UUID.randomUUID().toString());
        }
        if (paymentMethod.getCreatedAt() == null) {
            paymentMethod.setCreatedAt(Instant.now());
        }
        paymentMethod.setUpdatedAt(Instant.now());
        table.putItem(paymentMethod);
        return paymentMethod;
    }

    public Optional<PaymentMethod> findById(String paymentMethodId) {
        Key key = Key.builder().partitionValue(paymentMethodId).build();
        return Optional.ofNullable(table.getItem(key));
    }

    public List<PaymentMethod> findByUserId(String userId) {
        QueryConditional queryConditional = QueryConditional.keyEqualTo(
            Key.builder().partitionValue(userId).build()
        );

        return userIdIndex.query(queryConditional)
            .stream()
            .flatMap(page -> page.items().stream())
            .collect(Collectors.toList());
    }

    public Optional<PaymentMethod> findDefaultByUserId(String userId) {
        return findByUserId(userId).stream()
            .filter(pm -> Boolean.TRUE.equals(pm.getIsDefault()))
            .findFirst();
    }

    public void setAsDefault(String userId, String paymentMethodId) {
        List<PaymentMethod> methods = findByUserId(userId);
        for (PaymentMethod method : methods) {
            boolean shouldBeDefault = method.getPaymentMethodId().equals(paymentMethodId);
            Boolean currentDefault = method.getIsDefault();
            if (shouldBeDefault != Boolean.TRUE.equals(currentDefault)) {
                method.setIsDefault(shouldBeDefault);
                save(method);
            }
        }
    }

    public void delete(String paymentMethodId) {
        Key key = Key.builder().partitionValue(paymentMethodId).build();
        table.deleteItem(key);
    }
}
