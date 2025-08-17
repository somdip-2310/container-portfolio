package dev.somdip.containerplatform.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class User {
    
    private String userId;
    private String email;
    private String passwordHash;
    private String username;
    private String fullName;
    private String apiKey;
    private UserPlan plan;
    private UserStatus status;
    private Set<String> roles;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastLoginAt;
    private String stripeCustomerId;
    private String stripeSubscriptionId;
    private Boolean emailVerified;
    private String emailVerificationToken;
    private String passwordResetToken;
    private Instant passwordResetExpiry;
    private Integer containerCount;
    private Long totalDeployments;
    private UsageStats usageStats;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("userId")
    public String getUserId() {
        return userId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "EmailIndex")
    @DynamoDbAttribute("email")
    public String getEmail() {
        return email;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "ApiKeyIndex")
    @DynamoDbAttribute("apiKey")
    public String getApiKey() {
        return apiKey;
    }

    @DynamoDbAttribute("plan")
    public UserPlan getPlan() {
        return plan;
    }

    @DynamoDbAttribute("status")
    public UserStatus getStatus() {
        return status;
    }

    @DynamoDbAttribute("roles")
    public Set<String> getRoles() {
        return roles;
    }

    @DynamoDbAttribute("createdAt")
    public Instant getCreatedAt() {
        return createdAt;
    }

    @DynamoDbAttribute("updatedAt")
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @DynamoDbAttribute("usageStats")
    public UsageStats getUsageStats() {
        return usageStats;
    }

    public enum UserPlan {
        FREE, STARTER, PRO, SCALE, ENTERPRISE
    }

    public enum UserStatus {
        ACTIVE, INACTIVE, SUSPENDED, PENDING_VERIFICATION
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @DynamoDbBean
    public static class UsageStats {
        private Long totalCpuHours;
        private Long totalMemoryGbHours;
        private Long totalBandwidthGb;
        private Long totalStorageGb;
        private Instant periodStart;
        private Instant periodEnd;
    }
}