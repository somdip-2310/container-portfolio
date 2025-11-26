package dev.somdip.containerplatform.dto.feedback;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackResponse {
    private boolean success;
    private String message;
    private Double bonusHoursAwarded;
    private Double newTotalBonus;
    private Double remainingHours;
}
