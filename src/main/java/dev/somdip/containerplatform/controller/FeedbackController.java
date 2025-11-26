package dev.somdip.containerplatform.controller;

import dev.somdip.containerplatform.dto.feedback.BugReportRequest;
import dev.somdip.containerplatform.dto.feedback.FeedbackRequest;
import dev.somdip.containerplatform.dto.feedback.FeedbackResponse;
import dev.somdip.containerplatform.model.User;
import dev.somdip.containerplatform.service.EmailService;
import dev.somdip.containerplatform.service.UsageTrackingService;
import dev.somdip.containerplatform.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/web/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final UserService userService;
    private final EmailService emailService;
    private final UsageTrackingService usageTrackingService;

    // TESTING VALUES: 5 minutes = 0.0833 hours
    // TODO: Change back to 50.0 for production
    private static final double FEEDBACK_BONUS_HOURS = 0.0833; // 5 minutes for testing
    private static final double BUG_REPORT_MIN_BONUS = 0.0;
    private static final double BUG_REPORT_MAX_BONUS = 0.0833; // 5 minutes for testing (was 50.0)

    @PostMapping("/submit")
    public ResponseEntity<FeedbackResponse> submitFeedback(
            @Valid @RequestBody FeedbackRequest request,
            Authentication authentication) {

        try {
            String username = authentication.getName();
            User user = userService.findByEmail(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

            // Send feedback email to contact@snapdeploy.dev
            emailService.sendFeedbackNotification(
                user.getEmail(),
                user.getName(),
                request.getMessage(),
                request.getCategory()
            );

            // Award bonus hours (only for FREE tier users)
            if (user.getPlan() == User.UserPlan.FREE) {
                usageTrackingService.addBonusHours(
                    user.getUserId(),
                    FEEDBACK_BONUS_HOURS,
                    "Feedback submission"
                );

                // Refresh user to get updated bonus hours
                user = userService.findByEmail(username).orElseThrow();

                double remainingHours = usageTrackingService.getRemainingHours(user);

                return ResponseEntity.ok(FeedbackResponse.builder()
                    .success(true)
                    .message("Thank you for your feedback! You've been awarded 5 bonus minutes.") // TESTING: was "50 bonus hours"
                    .bonusHoursAwarded(FEEDBACK_BONUS_HOURS)
                    .newTotalBonus(user.getBonusHours())
                    .remainingHours(remainingHours)
                    .build());
            }

            return ResponseEntity.ok(FeedbackResponse.builder()
                .success(true)
                .message("Thank you for your feedback!")
                .bonusHoursAwarded(0.0)
                .build());

        } catch (Exception e) {
            log.error("Error processing feedback", e);
            return ResponseEntity.badRequest().body(FeedbackResponse.builder()
                .success(false)
                .message("Failed to submit feedback: " + e.getMessage())
                .build());
        }
    }

    @PostMapping("/bug-report")
    public ResponseEntity<FeedbackResponse> submitBugReport(
            @Valid @RequestBody BugReportRequest request,
            Authentication authentication) {

        try {
            String username = authentication.getName();
            User user = userService.findByEmail(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

            // Send bug report email to contact@snapdeploy.dev
            emailService.sendBugReportNotification(
                user.getEmail(),
                user.getName(),
                request.getTitle(),
                request.getDescription(),
                request.getStepsToReproduce(),
                request.getSeverity() != null ? request.getSeverity() : "medium"
            );

            // Note: Bonus hours for bug reports are awarded manually after review
            return ResponseEntity.ok(FeedbackResponse.builder()
                .success(true)
                .message("Bug report submitted successfully! We'll review it and award bonus minutes (0-5 minutes or extended access) based on the severity and validity.") // TESTING: was "0-50 hours or 1 month"
                .bonusHoursAwarded(0.0)
                .build());

        } catch (Exception e) {
            log.error("Error processing bug report", e);
            return ResponseEntity.badRequest().body(FeedbackResponse.builder()
                .success(false)
                .message("Failed to submit bug report: " + e.getMessage())
                .build());
        }
    }
}
