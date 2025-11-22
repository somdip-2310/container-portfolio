package dev.somdip.containerplatform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client;
import software.amazon.awssdk.services.route53.Route53Client;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Configuration

public class AwsConfig {
	
	private static final Logger log = LoggerFactory.getLogger(AwsConfig.class);
	
    @Value("${aws.region}")
    private String awsRegion;

    @Bean
    @Primary
    public AwsCredentialsProvider awsCredentialsProvider() {
        log.info("Configuring AWS credentials provider chain");
        return DefaultCredentialsProvider.builder().build();
    }

    @Bean
    public Region awsRegion() {
        return Region.of(awsRegion);
    }

    @Bean
    public EcsClient ecsClient(AwsCredentialsProvider credentialsProvider, Region region) {
        log.info("Creating ECS client for region: {}", region);
        return EcsClient.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .build();
    }

    @Bean
    public DynamoDbClient dynamoDbClient(AwsCredentialsProvider credentialsProvider, Region region) {
        log.info("Creating DynamoDB client for region: {}", region);
        return DynamoDbClient.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .build();
    }

    @Bean
    public S3Client s3Client(AwsCredentialsProvider credentialsProvider, Region region) {
        log.info("Creating S3 client for region: {}", region);
        return S3Client.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .build();
    }

    @Bean
    public SecretsManagerClient secretsManagerClient(AwsCredentialsProvider credentialsProvider, Region region) {
        log.info("Creating Secrets Manager client for region: {}", region);
        return SecretsManagerClient.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .build();
    }

    @Bean
    public CloudWatchClient cloudWatchClient(AwsCredentialsProvider credentialsProvider, Region region) {
        log.info("Creating CloudWatch client for region: {}", region);
        return CloudWatchClient.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .build();
    }

    @Bean
    public CloudWatchLogsClient cloudWatchLogsClient(AwsCredentialsProvider credentialsProvider, Region region) {
        log.info("Creating CloudWatch Logs client for region: {}", region);
        return CloudWatchLogsClient.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .build();
    }

    @Bean
    public ElasticLoadBalancingV2Client elasticLoadBalancingV2Client(AwsCredentialsProvider credentialsProvider, Region region) {
        log.info("Creating ELB v2 client for region: {}", region);
        return ElasticLoadBalancingV2Client.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .build();
    }

    @Bean
    public Route53Client route53Client(AwsCredentialsProvider credentialsProvider) {
        log.info("Creating Route53 client");
        return Route53Client.builder()
                .credentialsProvider(credentialsProvider)
                .region(Region.AWS_GLOBAL) // Route53 is a global service
                .build();
    }

    @Bean
    public EcrClient ecrClient(AwsCredentialsProvider credentialsProvider, Region region) {
        log.info("Creating ECR client for region: {}", region);
        return EcrClient.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .build();
    }

    @Bean
    public Ec2Client ec2Client(AwsCredentialsProvider credentialsProvider, Region region) {
        log.info("Creating EC2 client for region: {}", region);
        return Ec2Client.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .build();
    }
}