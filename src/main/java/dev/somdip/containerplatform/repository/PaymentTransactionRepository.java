package dev.somdip.containerplatform.repository;

import dev.somdip.containerplatform.model.PaymentTransaction;
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
public class PaymentTransactionRepository {

    private final DynamoDbTable<PaymentTransaction> table;
    private final DynamoDbIndex<PaymentTransaction> userIdIndex;
    private final DynamoDbIndex<PaymentTransaction> razorpayOrderIdIndex;
    private final DynamoDbIndex<PaymentTransaction> razorpayPaymentIdIndex;

    public PaymentTransactionRepository(DynamoDbEnhancedClient enhancedClient,
                                        @Qualifier("paymentTransactionsTableName") String tableName) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(PaymentTransaction.class));
        this.userIdIndex = table.index("UserIdIndex");
        this.razorpayOrderIdIndex = table.index("RazorpayOrderIdIndex");
        this.razorpayPaymentIdIndex = table.index("RazorpayPaymentIdIndex");
    }

    public PaymentTransaction save(PaymentTransaction transaction) {
        if (transaction.getTransactionId() == null) {
            transaction.setTransactionId(UUID.randomUUID().toString());
        }
        if (transaction.getCreatedAt() == null) {
            transaction.setCreatedAt(Instant.now());
        }
        transaction.setUpdatedAt(Instant.now());
        table.putItem(transaction);
        return transaction;
    }

    public Optional<PaymentTransaction> findById(String transactionId) {
        Key key = Key.builder().partitionValue(transactionId).build();
        return Optional.ofNullable(table.getItem(key));
    }

    public List<PaymentTransaction> findByUserId(String userId) {
        QueryConditional queryConditional = QueryConditional.keyEqualTo(
            Key.builder().partitionValue(userId).build()
        );

        return userIdIndex.query(queryConditional)
            .stream()
            .flatMap(page -> page.items().stream())
            .collect(Collectors.toList());
    }

    public Optional<PaymentTransaction> findByRazorpayOrderId(String razorpayOrderId) {
        QueryConditional queryConditional = QueryConditional.keyEqualTo(
            Key.builder().partitionValue(razorpayOrderId).build()
        );

        return razorpayOrderIdIndex.query(queryConditional)
            .stream()
            .flatMap(page -> page.items().stream())
            .findFirst();
    }

    public Optional<PaymentTransaction> findByRazorpayPaymentId(String razorpayPaymentId) {
        QueryConditional queryConditional = QueryConditional.keyEqualTo(
            Key.builder().partitionValue(razorpayPaymentId).build()
        );

        return razorpayPaymentIdIndex.query(queryConditional)
            .stream()
            .flatMap(page -> page.items().stream())
            .findFirst();
    }

    public void delete(String transactionId) {
        Key key = Key.builder().partitionValue(transactionId).build();
        table.deleteItem(key);
    }
}
