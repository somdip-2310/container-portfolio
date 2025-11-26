package dev.somdip.containerplatform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageLimitDTO {
    private Double hoursUsed;
    private Double bonusHours;
    private Double totalLimit; // 200 + bonusHours
    private Double remainingHours;
    private Double percentageUsed;
    private String warningLevel; // "none", "warning" (75%), "critical" (95%), "exceeded" (100%)
    private Boolean canStartContainers;
    private String planName;

    // Usage projection
    private Double hoursUsedThisMonth;
    private Integer monthsRemainingEstimate; // Based on current usage rate

    // Marketing messages
    private String primaryMessage;
    private String secondaryMessage;
}
