package dev.somdip.containerplatform.dto.feedback;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
public class BugReportRequest {

    @NotBlank(message = "Bug title is required")
    @Size(min = 10, max = 200, message = "Title must be between 10 and 200 characters")
    private String title;

    @NotBlank(message = "Bug description is required")
    @Size(min = 30, max = 2000, message = "Description must be between 30 and 2000 characters")
    private String description;

    @NotBlank(message = "Steps to reproduce are required")
    @Size(min = 20, max = 1000, message = "Steps must be between 20 and 1000 characters")
    private String stepsToReproduce;

    @Size(max = 500, message = "Expected behavior must not exceed 500 characters")
    private String expectedBehavior;

    @Size(max = 500, message = "Actual behavior must not exceed 500 characters")
    private String actualBehavior;

    private String severity; // "critical", "high", "medium", "low"

    private String browserInfo; // Optional: browser and version

    private String screenshotUrl; // Optional: uploaded screenshot URL
}
