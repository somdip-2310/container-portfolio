package dev.somdip.containerplatform.service;

import dev.somdip.containerplatform.model.User;
import dev.somdip.containerplatform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsScheduler {
    
    private final MetricsService metricsService;
    private final UserRepository userRepository;
    
    /**
     * Update metrics for all users' running containers every 5 minutes
     */
    @Scheduled(fixedRate = 300000) // 5 minutes in milliseconds
    public void updateAllMetrics() {
        log.info("Starting scheduled metrics update for all users");
        
        try {
            List<User> users = userRepository.findAll();
            
            for (User user : users) {
                try {
                    log.debug("Updating metrics for user: {}", user.getUserId());
                    metricsService.updateAllUserContainerMetricsAsync(user.getUserId());
                } catch (Exception e) {
                    log.error("Error updating metrics for user {}: {}", user.getUserId(), e.getMessage());
                }
            }
            
            log.info("Scheduled metrics update initiated for {} users", users.size());
            
        } catch (Exception e) {
            log.error("Error in scheduled metrics update: {}", e.getMessage(), e);
        }
    }
}
