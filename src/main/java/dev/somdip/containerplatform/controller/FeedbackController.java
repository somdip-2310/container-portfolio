package dev.somdip.containerplatform.controller;

import dev.somdip.containerplatform.dto.feedback.BugReportRequest;
import dev.somdip.containerplatform.dto.feedback.FeedbackRequest;
import dev.somdip.containerplatform.dto.feedback.FeedbackResponse;
import dev.somdip.containerplatform.model.User;
import dev.somdip.containerplatform.repository.UserRepository;
import dev.somdip.containerplatform.service.EmailService;
import dev.somdip.containerplatform.service.UsageTrackingService;
import dev.somdip.containerplatform.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@Slf4j
@RestController
@RequestMapping("/web/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final UsageTrackingService usageTrackingService;

    // TESTING VALUES: 5 minutes = 0.0833 hours
    // TODO: Change back to 50.0 for production
    private static final double FEEDBACK_BONUS_HOURS = 0.0833; // 5 minutes for testing
    private static final double BUG_REPORT_MIN_BONUS = 0.0;
    private static final double BUG_REPORT_MAX_BONUS = 0.0833; // 5 minutes for testing (was 50.0)
    
    // Rate limiting constants
    private static final int MAX_FEEDBACK_PER_HOUR = 3;
    private static final int MAX_FEEDBACK_PER_DAY = 10;


    /**
     * Check if user has exceeded rate limits for feedback submissions
     * Updates counters and resets them if time windows have expired
     * 
     * @param user The user submitting feedback
     * @return true if rate limit exceeded, false if allowed
     */
    private boolean isRateLimitExceeded(User user) {
        Instant now = Instant.now();
        
        // Initialize counters if null
        if (user.getHourlyFeedbackCount() == null) {
            user.setHourlyFeedbackCount(0);
            user.setHourlyFeedbackResetAt(now.plusSeconds(3600)); // 1 hour from now
        }
        if (user.getDailyFeedbackCount() == null) {
            user.setDailyFeedbackCount(0);
            user.setDailyFeedbackResetAt(now.plusSeconds(86400)); // 24 hours from now
        }
        
        // Reset hourly counter if window expired
        if (user.getHourlyFeedbackResetAt() != null && now.isAfter(user.getHourlyFeedbackResetAt())) {
            user.setHourlyFeedbackCount(0);
            user.setHourlyFeedbackResetAt(now.plusSeconds(3600));
        }
        
        // Reset daily counter if window expired
        if (user.getDailyFeedbackResetAt() != null && now.isAfter(user.getDailyFeedbackResetAt())) {
            user.setDailyFeedbackCount(0);
            user.setDailyFeedbackResetAt(now.plusSeconds(86400));
        }
        
        // Check limits
        if (user.getHourlyFeedbackCount() >= MAX_FEEDBACK_PER_HOUR) {
            log.warn("User {} exceeded hourly feedback limit ({}/{})", 
                user.getEmail(), user.getHourlyFeedbackCount(), MAX_FEEDBACK_PER_HOUR);
            return true;
        }
        
        if (user.getDailyFeedbackCount() >= MAX_FEEDBACK_PER_DAY) {
            log.warn("User {} exceeded daily feedback limit ({}/{})", 
                user.getEmail(), user.getDailyFeedbackCount(), MAX_FEEDBACK_PER_DAY);
            return true;
        }
        
        return false;
    }
    
    /**
     * Increment feedback submission counters
     */
    private void incrementFeedbackCounters(User user) {
        Instant now = Instant.now();
        
        user.setHourlyFeedbackCount(user.getHourlyFeedbackCount() + 1);
        user.setDailyFeedbackCount(user.getDailyFeedbackCount() + 1);
        user.setLastFeedbackSubmittedAt(now);
        user.setUpdatedAt(now);
        
        userRepository.save(user);
        
        log.info("Updated feedback counters for {}: hourly={}/{}, daily={}/{}", 
            user.getEmail(), 
            user.getHourlyFeedbackCount(), MAX_FEEDBACK_PER_HOUR,
            user.getDailyFeedbackCount(), MAX_FEEDBACK_PER_DAY);
    }

        @PostMapping("/submit")
    public ResponseEntity<FeedbackResponse> submitFeedback(
            @Valid @RequestBody FeedbackRequest request,
            Authentication authentication) {

        try {
            String username = authentication.getName();
            User user = userService.findByEmail(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

            // Check rate limiting
            if (isRateLimitExceeded(user)) {
                long hourlyRemaining = user.getHourlyFeedbackResetAt() != null ? 
                    (user.getHourlyFeedbackResetAt().getEpochSecond() - Instant.now().getEpochSecond()) / 60 : 0;
                long dailyRemaining = user.getDailyFeedbackResetAt() != null ? 
                    (user.getDailyFeedbackResetAt().getEpochSecond() - Instant.now().getEpochSecond()) / 3600 : 0;
                    
                String message = String.format(
                    "Rate limit exceeded. You can submit up to %d feedback per hour and %d per day. " +
                    "Please try again in %d minutes (hourly) or %d hours (daily).",
                    MAX_FEEDBACK_PER_HOUR, MAX_FEEDBACK_PER_DAY, hourlyRemaining, dailyRemaining
                );
                
                log.warn("Rate limit exceeded for user {}: hourly={}, daily={}", 
                    user.getEmail(), user.getHourlyFeedbackCount(), user.getDailyFeedbackCount());
                    
                return ResponseEntity.status(429).body(FeedbackResponse.builder()
                    .success(false)
                    .message(message)
                    .bonusHoursAwarded(0.0)
                    .build());
            }

            // Increment feedback counters
            incrementFeedbackCounters(user);

            // ALWAYS send feedback email to contact@snapdeploy.dev (every submission)
            log.info("Sending feedback email for user: {} ({})", user.getName(), user.getEmail());
            emailService.sendFeedbackNotification(
                user.getEmail(),
                user.getName(),
                request.getMessage(),
                request.getCategory()
            );
            log.info("Feedback email sent successfully to contact@snapdeploy.dev");

            // Award bonus hours (only for FREE tier users and only once)
            if (user.getPlan() == User.UserPlan.FREE) {
                // Check if bonus was already awarded
                boolean alreadyAwarded = user.getFeedbackBonusAwarded() != null && user.getFeedbackBonusAwarded();

                if (!alreadyAwarded) {
                    // Award bonus hours and mark as awarded
                    log.info("Awarding first-time feedback bonus to user: {}", user.getEmail());
                    usageTrackingService.addBonusHours(
                        user.getUserId(),
                        FEEDBACK_BONUS_HOURS,
                        "Feedback submission"
                    );

                    // Mark feedback bonus as awarded
                    user.setFeedbackBonusAwarded(true);
                    user.setUpdatedAt(Instant.now());
                    userRepository.save(user);

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
                } else {
                    // Already received bonus - email was still sent above
                    log.info("User {} already received feedback bonus, but email was sent", user.getEmail());
                    return ResponseEntity.ok(FeedbackResponse.builder()
                        .success(true)
                        .message("Thank you for your feedback! (You've already received your one-time feedback bonus)")
                        .bonusHoursAwarded(0.0)
                        .build());
                }
            }

            // Non-FREE tier users - email was still sent above
            log.info("Feedback received from non-FREE user: {}", user.getEmail());
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
