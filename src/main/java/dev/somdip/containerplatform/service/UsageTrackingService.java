package dev.somdip.containerplatform.service;

import dev.somdip.containerplatform.model.Container;
import dev.somdip.containerplatform.model.User;
import dev.somdip.containerplatform.repository.ContainerRepository;
import dev.somdip.containerplatform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.*;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsageTrackingService {

    private final UserRepository userRepository;
    private final ContainerRepository containerRepository;
    private final ContainerService containerService;
    private final EcsClient ecsClient;

    @Value("${aws.ecs.cluster}")
    private String clusterName;

    // FREE plan limit: 200 hours
    public static final double FREE_PLAN_HOURS_LIMIT = 200.0;

    // Cache to store last tracking time for each container
    private final Map<String, Instant> lastTrackingTime = new HashMap<>();

    /**
     * Track container hours usage every 15 minutes
     * Uses actual AWS ECS task runtime for accurate billing
     */
    @Scheduled(cron = "0 */15 * * * *") // Every 15 minutes
    public void trackContainerHours() {
        log.info("Starting container usage tracking (every 15 minutes)");

        try {
            List<User> allUsers = userRepository.findAll();

            for (User user : allUsers) {
                try {
                    updateUserHoursUsedFromECS(user);
                } catch (Exception e) {
                    log.error("Error tracking hours for user: {}", user.getUserId(), e);
                }
            }

            log.info("Completed container usage tracking for {} users", allUsers.size());

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

    /**
     * Update user hours based on actual ECS task runtime from AWS
     * This method queries AWS ECS to get exact task start times and calculates actual hours
     */
    private void updateUserHoursUsedFromECS(User user) {
        List<Container> userContainers = containerRepository.findByUserId(user.getUserId());

        double totalHoursToAdd = 0.0;
        Instant now = Instant.now();

        for (Container container : userContainers) {
            if (container.getStatus() != Container.ContainerStatus.RUNNING) {
                continue; // Only track running containers
            }

            if (container.getServiceArn() == null) {
                log.warn("Container {} has no service ARN, skipping", container.getContainerId());
                continue;
            }

            try {
                // Get actual runtime from ECS
                double hoursForThisContainer = getActualContainerRuntime(container, now);
                totalHoursToAdd += hoursForThisContainer;

                log.debug("Container {}: added {} hours", container.getContainerId(), hoursForThisContainer);

            } catch (Exception e) {
                log.error("Error getting runtime for container {}: {}", container.getContainerId(), e.getMessage());
            }
        }

        if (totalHoursToAdd == 0) {
            return; // No hours to add
        }

        // Update user's total hours
        double currentHours = user.getHoursUsed() != null ? user.getHoursUsed() : 0.0;
        double newHours = currentHours + totalHoursToAdd;

        user.setHoursUsed(newHours);
        user.setUpdatedAt(now);

        // Set reset time if not already set
        if (user.getUsageResetAt() == null) {
            user.setUsageResetAt(now.plus(30, ChronoUnit.DAYS));
        }

        userRepository.save(user);

        log.info("Updated usage for user {}: {} hours used (added {} hours this interval)",
            user.getUserId(), newHours, totalHoursToAdd);

        // Check if approaching limit (90% threshold)
        if (user.getPlan() == User.UserPlan.FREE && newHours >= FREE_PLAN_HOURS_LIMIT * 0.9) {
            log.warn("User {} is approaching FREE plan limit: {} / {} hours",
                user.getUserId(), newHours, FREE_PLAN_HOURS_LIMIT);
        }
    }

    /**
     * Get actual container runtime from AWS ECS tasks
     * Returns hours elapsed since last check (or since task start if first check)
     */
    private double getActualContainerRuntime(Container container, Instant now) {
        String containerId = container.getContainerId();

        try {
            // Describe the ECS tasks for this service
            DescribeServicesRequest request = DescribeServicesRequest.builder()
                .cluster(clusterName)
                .services(container.getServiceArn())
                .build();

            DescribeServicesResponse response = ecsClient.describeServices(request);

            if (response.services().isEmpty()) {
                log.warn("No ECS service found for container {}", containerId);
                return 0.0;
            }

            software.amazon.awssdk.services.ecs.model.Service ecsService = response.services().get(0);

            // Get the task ARNs for this service
            ListTasksRequest listTasksRequest = ListTasksRequest.builder()
                .cluster(clusterName)
                .serviceName(ecsService.serviceName())
                .desiredStatus(DesiredStatus.RUNNING)
                .build();

            ListTasksResponse listTasksResponse = ecsClient.listTasks(listTasksRequest);

            if (listTasksResponse.taskArns().isEmpty()) {
                log.debug("No running tasks for container {}", containerId);
                return 0.0;
            }

            // Describe the tasks to get start time
            DescribeTasksRequest describeTasksRequest = DescribeTasksRequest.builder()
                .cluster(clusterName)
                .tasks(listTasksResponse.taskArns())
                .build();

            DescribeTasksResponse describeTasksResponse = ecsClient.describeTasks(describeTasksRequest);

            if (describeTasksResponse.tasks().isEmpty()) {
                return 0.0;
            }

            // Get the first running task (there should typically be one per service)
            Task task = describeTasksResponse.tasks().get(0);
            Instant taskStartTime = task.startedAt();

            if (taskStartTime == null) {
                log.warn("Task for container {} has no start time", containerId);
                return 0.0;
            }

            // Calculate hours since last check (or since task start)
            Instant lastCheck = lastTrackingTime.getOrDefault(containerId, taskStartTime);

            // Ensure we don't count time before the task actually started
            if (lastCheck.isBefore(taskStartTime)) {
                lastCheck = taskStartTime;
            }

            // Calculate duration in hours
            Duration duration = Duration.between(lastCheck, now);
            double hours = duration.toMinutes() / 60.0;

            // Update last tracking time for this container
            lastTrackingTime.put(containerId, now);

            return Math.max(0, hours); // Ensure non-negative

        } catch (Exception e) {
            log.error("Error querying ECS for container {}: {}", containerId, e.getMessage());
            return 0.0;
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
