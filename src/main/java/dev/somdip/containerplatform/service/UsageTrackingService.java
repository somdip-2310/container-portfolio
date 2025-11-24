package dev.somdip.containerplatform.service;

import dev.somdip.containerplatform.model.Container;
import dev.somdip.containerplatform.model.User;
import dev.somdip.containerplatform.repository.ContainerRepository;
import dev.somdip.containerplatform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsageTrackingService {

    private final UserRepository userRepository;
    private final ContainerRepository containerRepository;
    private final ContainerService containerService;

    // FREE plan limit: 200 hours
    public static final double FREE_PLAN_HOURS_LIMIT = 200.0;

    /**
     * Track container hours usage every hour
     * For each user, calculate hours used by running containers
     */
    @Scheduled(cron = "0 0 * * * *") // Every hour at minute 0
    public void trackContainerHours() {
        log.info("Starting hourly container usage tracking");

        try {
            List<User> allUsers = userRepository.findAll();

            for (User user : allUsers) {
                try {
                    updateUserHoursUsed(user);
                } catch (Exception e) {
                    log.error("Error tracking hours for user: {}", user.getUserId(), e);
                }
            }

            log.info("Completed hourly container usage tracking for {} users", allUsers.size());

        } catch (Exception e) {
            log.error("Error in usage tracking job", e);
        }
    }

    /**
     * Check FREE plan limits every 10 minutes and auto-shutdown containers if needed
     */
    @Scheduled(cron = "0 */10 * * * *") // Every 10 minutes
    public void checkFreePlanLimits() {
        log.info("Checking FREE plan usage limits");

        try {
            List<User> freeUsers = userRepository.findByPlan(User.UserPlan.FREE);

            for (User user : freeUsers) {
                try {
                    if (hasExceededFreeLimit(user)) {
                        log.warn("User {} has exceeded FREE plan limit ({} hours used)",
                            user.getUserId(), user.getHoursUsed());
                        shutdownUserContainers(user);
                    }
                } catch (Exception e) {
                    log.error("Error checking limits for user: {}", user.getUserId(), e);
                }
            }

        } catch (Exception e) {
            log.error("Error in FREE plan limit check job", e);
        }
    }

    /**
     * Reset monthly usage counters for FREE plan users
     * Runs on the 1st of each month at midnight
     */
    @Scheduled(cron = "0 0 0 1 * *") // 1st day of month at 00:00
    public void resetMonthlyUsage() {
        log.info("Resetting monthly usage counters for FREE plan users");

        try {
            List<User> freeUsers = userRepository.findByPlan(User.UserPlan.FREE);

            for (User user : freeUsers) {
                try {
                    user.setHoursUsed(0.0);
                    user.setUsageResetAt(Instant.now().plus(30, ChronoUnit.DAYS));
                    userRepository.save(user);
                    log.info("Reset usage for user: {}", user.getUserId());
                } catch (Exception e) {
                    log.error("Error resetting usage for user: {}", user.getUserId(), e);
                }
            }

            log.info("Completed monthly usage reset for {} users", freeUsers.size());

        } catch (Exception e) {
            log.error("Error in monthly usage reset job", e);
        }
    }

    private void updateUserHoursUsed(User user) {
        List<Container> userContainers = containerRepository.findByUserId(user.getUserId());

        // Count running containers
        long runningContainers = userContainers.stream()
            .filter(c -> c.getStatus() == Container.ContainerStatus.RUNNING)
            .count();

        if (runningContainers == 0) {
            return; // No running containers, no hours to add
        }

        // Add 1 hour for each running container
        double currentHours = user.getHoursUsed() != null ? user.getHoursUsed() : 0.0;
        double newHours = currentHours + runningContainers;

        user.setHoursUsed(newHours);
        user.setUpdatedAt(Instant.now());

        // Set reset time if not already set
        if (user.getUsageResetAt() == null) {
            user.setUsageResetAt(Instant.now().plus(30, ChronoUnit.DAYS));
        }

        userRepository.save(user);

        log.info("Updated usage for user {}: {} hours used ({} running containers)",
            user.getUserId(), newHours, runningContainers);

        // Check if approaching limit (90% threshold)
        if (user.getPlan() == User.UserPlan.FREE && newHours >= FREE_PLAN_HOURS_LIMIT * 0.9) {
            log.warn("User {} is approaching FREE plan limit: {} / {} hours",
                user.getUserId(), newHours, FREE_PLAN_HOURS_LIMIT);
        }
    }

    private boolean hasExceededFreeLimit(User user) {
        if (user.getPlan() != User.UserPlan.FREE) {
            return false; // Only applies to FREE plan
        }

        double hoursUsed = user.getHoursUsed() != null ? user.getHoursUsed() : 0.0;
        return hoursUsed >= FREE_PLAN_HOURS_LIMIT;
    }

    private void shutdownUserContainers(User user) {
        List<Container> userContainers = containerRepository.findByUserId(user.getUserId());

        for (Container container : userContainers) {
            if (container.getStatus() == Container.ContainerStatus.RUNNING) {
                try {
                    log.info("Auto-stopping container {} for user {} (FREE limit exceeded)",
                        container.getContainerId(), user.getUserId());

                    containerService.stopContainer(container.getContainerId());

                } catch (Exception e) {
                    log.error("Error stopping container {} for user {}",
                        container.getContainerId(), user.getUserId(), e);
                }
            }
        }

        log.info("Auto-stopped all running containers for user {} (FREE limit exceeded)",
            user.getUserId());
    }

    /**
     * Get remaining hours for a user (mainly for FREE plan)
     */
    public double getRemainingHours(User user) {
        if (user.getPlan() != User.UserPlan.FREE) {
            return -1; // Unlimited for paid plans
        }

        double hoursUsed = user.getHoursUsed() != null ? user.getHoursUsed() : 0.0;
        double remaining = FREE_PLAN_HOURS_LIMIT - hoursUsed;
        return Math.max(0, remaining);
    }

    /**
     * Check if user can start a new container
     */
    public boolean canStartContainer(User user) {
        if (user.getPlan() != User.UserPlan.FREE) {
            return true; // Paid plans have no hourly limits
        }

        return !hasExceededFreeLimit(user);
    }
}
