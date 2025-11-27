package dev.somdip.containerplatform.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;


import java.time.Instant;
import java.util.Set;


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
    private Double hoursUsed; // Total container hours used (for FREE plan limit)
    private Double bonusHours; // Bonus hours earned (feedback, bug reports, etc.)
    private Boolean feedbackBonusAwarded; // Track if feedback bonus has been awarded
    
    // Rate limiting fields for feedback/bug reports
    private Instant lastFeedbackSubmittedAt; // Last time user submitted feedback
    private Integer hourlyFeedbackCount; // Feedback count in current hour
    private Integer dailyFeedbackCount; // Feedback count in current day
    private Instant hourlyFeedbackResetAt; // When hourly counter resets
    private Instant dailyFeedbackResetAt; // When daily counter resets
    private Instant usageResetAt; // When usage counter resets (for FREE plan monthly reset)
    private UsageStats usageStats;
    
    

    public String getPasswordHash() {
		return passwordHash;
	}

	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getFullName() {
		return fullName;
	}

	public void setFullName(String fullName) {
		this.fullName = fullName;
	}

	// Alias for getUsername() - commonly used in views
	public String getName() {
		return username != null ? username : fullName;
	}

	// Alias for getPlan() - subscription plan
	public UserPlan getSubscriptionPlan() {
		return plan;
	}

	// Alias for getStatus() - subscription status
	public UserStatus getSubscriptionStatus() {
		return status;
	}

	public Instant getLastLoginAt() {
		return lastLoginAt;
	}

	public void setLastLoginAt(Instant lastLoginAt) {
		this.lastLoginAt = lastLoginAt;
	}

	public String getStripeCustomerId() {
		return stripeCustomerId;
	}

	public void setStripeCustomerId(String stripeCustomerId) {
		this.stripeCustomerId = stripeCustomerId;
	}

	public String getStripeSubscriptionId() {
		return stripeSubscriptionId;
	}

	public void setStripeSubscriptionId(String stripeSubscriptionId) {
		this.stripeSubscriptionId = stripeSubscriptionId;
	}

	public Boolean getEmailVerified() {
		return emailVerified;
	}

	public void setEmailVerified(Boolean emailVerified) {
		this.emailVerified = emailVerified;
	}

	public String getEmailVerificationToken() {
		return emailVerificationToken;
	}

	public void setEmailVerificationToken(String emailVerificationToken) {
		this.emailVerificationToken = emailVerificationToken;
	}

	public String getPasswordResetToken() {
		return passwordResetToken;
	}

	public void setPasswordResetToken(String passwordResetToken) {
		this.passwordResetToken = passwordResetToken;
	}

	public Instant getPasswordResetExpiry() {
		return passwordResetExpiry;
	}

	public void setPasswordResetExpiry(Instant passwordResetExpiry) {
		this.passwordResetExpiry = passwordResetExpiry;
	}

	public Integer getContainerCount() {
		return containerCount;
	}

	public void setContainerCount(Integer containerCount) {
		this.containerCount = containerCount;
	}

	public Long getTotalDeployments() {
		return totalDeployments;
	}

	public void setTotalDeployments(Long totalDeployments) {
		this.totalDeployments = totalDeployments;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public void setPlan(UserPlan plan) {
		this.plan = plan;
	}

	public void setStatus(UserStatus status) {
		this.status = status;
	}

	public void setRoles(Set<String> roles) {
		this.roles = roles;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

	public void setUsageStats(UsageStats usageStats) {
		this.usageStats = usageStats;
	}

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

    @DynamoDbAttribute("hoursUsed")
    public Double getHoursUsed() {
        return hoursUsed;
    }

    public void setHoursUsed(Double hoursUsed) {
        this.hoursUsed = hoursUsed;
    }

    @DynamoDbAttribute("usageResetAt")
    public Instant getUsageResetAt() {
        return usageResetAt;
    }

    public void setUsageResetAt(Instant usageResetAt) {
        this.usageResetAt = usageResetAt;
    }

    @DynamoDbAttribute("bonusHours")
    public Double getBonusHours() {
        return bonusHours;
    }

    public void setBonusHours(Double bonusHours) {
        this.bonusHours = bonusHours;
    }

    @DynamoDbAttribute("feedbackBonusAwarded")
    public Boolean getFeedbackBonusAwarded() {
        return feedbackBonusAwarded;
    }

    public void setFeedbackBonusAwarded(Boolean feedbackBonusAwarded) {
        this.feedbackBonusAwarded = feedbackBonusAwarded;
    }
    @DynamoDbAttribute("lastFeedbackSubmittedAt")
    public Instant getLastFeedbackSubmittedAt() {
        return lastFeedbackSubmittedAt;
    }

    public void setLastFeedbackSubmittedAt(Instant lastFeedbackSubmittedAt) {
        this.lastFeedbackSubmittedAt = lastFeedbackSubmittedAt;
    }

    @DynamoDbAttribute("hourlyFeedbackCount")
    public Integer getHourlyFeedbackCount() {
        return hourlyFeedbackCount;
    }

    public void setHourlyFeedbackCount(Integer hourlyFeedbackCount) {
        this.hourlyFeedbackCount = hourlyFeedbackCount;
    }

    @DynamoDbAttribute("dailyFeedbackCount")
    public Integer getDailyFeedbackCount() {
        return dailyFeedbackCount;
    }

    public void setDailyFeedbackCount(Integer dailyFeedbackCount) {
        this.dailyFeedbackCount = dailyFeedbackCount;
    }

    @DynamoDbAttribute("hourlyFeedbackResetAt")
    public Instant getHourlyFeedbackResetAt() {
        return hourlyFeedbackResetAt;
    }

    public void setHourlyFeedbackResetAt(Instant hourlyFeedbackResetAt) {
        this.hourlyFeedbackResetAt = hourlyFeedbackResetAt;
    }

    @DynamoDbAttribute("dailyFeedbackResetAt")
    public Instant getDailyFeedbackResetAt() {
        return dailyFeedbackResetAt;
    }

    public void setDailyFeedbackResetAt(Instant dailyFeedbackResetAt) {
        this.dailyFeedbackResetAt = dailyFeedbackResetAt;
    }


    public enum UserPlan {
        FREE, STARTER, PRO, BUSINESS, ENTERPRISE
    }

    public enum UserStatus {
        ACTIVE, INACTIVE, SUSPENDED, PENDING_VERIFICATION
    }

    
    @DynamoDbBean
    public static class UsageStats {
        private Long totalCpuHours;
        private Long totalMemoryGbHours;
        private Long totalBandwidthGb;
        private Long totalStorageGb;
        private Instant periodStart;
        public Long getTotalCpuHours() {
			return totalCpuHours;
		}
		public void setTotalCpuHours(Long totalCpuHours) {
			this.totalCpuHours = totalCpuHours;
		}
		public Long getTotalMemoryGbHours() {
			return totalMemoryGbHours;
		}
		public void setTotalMemoryGbHours(Long totalMemoryGbHours) {
			this.totalMemoryGbHours = totalMemoryGbHours;
		}
		public Long getTotalBandwidthGb() {
			return totalBandwidthGb;
		}
		public void setTotalBandwidthGb(Long totalBandwidthGb) {
			this.totalBandwidthGb = totalBandwidthGb;
		}
		public Long getTotalStorageGb() {
			return totalStorageGb;
		}
		public void setTotalStorageGb(Long totalStorageGb) {
			this.totalStorageGb = totalStorageGb;
		}
		public Instant getPeriodStart() {
			return periodStart;
		}
		public void setPeriodStart(Instant periodStart) {
			this.periodStart = periodStart;
		}
		public Instant getPeriodEnd() {
			return periodEnd;
		}
		public void setPeriodEnd(Instant periodEnd) {
			this.periodEnd = periodEnd;
		}
		private Instant periodEnd;
    }
}