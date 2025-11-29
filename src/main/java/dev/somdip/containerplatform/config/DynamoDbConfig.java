package dev.somdip.containerplatform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Configuration
public class DynamoDbConfig {
	
	private static final Logger log = LoggerFactory.getLogger(DynamoDbConfig.class);

    @Value("${aws.dynamodb.tables.users}")
    private String usersTableName;

    @Value("${aws.dynamodb.tables.containers}")
    private String containersTableName;

    @Value("${aws.dynamodb.tables.deployments}")
    private String deploymentsTableName;

    @Value("${aws.dynamodb.tables.payment-transactions}")
    private String paymentTransactionsTableName;

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        log.info("Creating DynamoDB Enhanced Client");
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }

    @Bean(name = "usersTableName")
    public String usersTableName() {
        return usersTableName;
    }

    @Bean(name = "containersTableName")
    public String containersTableName() {
        return containersTableName;
    }

    @Bean(name = "deploymentsTableName")
    public String deploymentsTableName() {
        return deploymentsTableName;
    }

    @Bean(name = "paymentTransactionsTableName")
    public String paymentTransactionsTableName() {
        return paymentTransactionsTableName;
    }
}