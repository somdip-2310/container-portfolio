package dev.somdip.containerplatform.dto.feedback;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
public class FeedbackRequest {

    @NotBlank(message = "Feedback message is required")
    @Size(min = 20, max = 1000, message = "Feedback must be between 20 and 1000 characters")
    private String message;

    @Size(max = 100, message = "Email must not exceed 100 characters")
    private String contactEmail; // Optional - if user wants to be contacted

    private String category; // Optional: "feature", "usability", "performance", "other"
}
