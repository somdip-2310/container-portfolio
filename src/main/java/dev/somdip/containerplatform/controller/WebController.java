package dev.somdip.containerplatform.controller;

import dev.somdip.containerplatform.dto.DashboardStats;
import dev.somdip.containerplatform.dto.RecentActivity;
import dev.somdip.containerplatform.dto.Notification;
import dev.somdip.containerplatform.model.Container;
import dev.somdip.containerplatform.model.Deployment;
import dev.somdip.containerplatform.model.User;
import dev.somdip.containerplatform.repository.DeploymentRepository;
import dev.somdip.containerplatform.service.ContainerService;
import dev.somdip.containerplatform.service.DashboardService;
import dev.somdip.containerplatform.service.MetricsService;
import dev.somdip.containerplatform.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WebController {
	
    

    private final DashboardService dashboardService;
    private final ContainerService containerService;
    private final UserService userService;
    private final MetricsService metricsService;
    private final DeploymentRepository deploymentRepository;

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String register() {
        return "register";
    }

    @GetMapping("/forgot-password")
    public String forgotPassword() {
        return "forgot-password";
    }

    @GetMapping("/verify-otp")
    public String verifyOtp() {
        return "verify-otp";
    }

    @GetMapping("/reset-password")
    public String resetPassword() {
        return "reset-password";
    }
    

    @GetMapping("/dashboard")
    public String dashboard(Model model, Authentication authentication) {
        if (authentication == null) {
            return "redirect:/login";
        }

        try {
            // Get user ID from authentication
            String username = authentication.getName();
            User user = userService.findByEmail(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
            String userId = user.getUserId();
            // Get containers
            List<Container> containers = metricsService.getUserContainers(userId);

            // If containers don't have metrics yet, update synchronously once
            boolean needsMetricsUpdate = containers.stream()
                .anyMatch(c -> c.getStatus() == Container.ContainerStatus.RUNNING && 
                              (c.getResourceUsage() == null || c.getResourceUsage().getAvgCpuPercent() == null));

            if (needsMetricsUpdate) {
                log.info("Containers missing metrics, updating synchronously for dashboard");
                containers = metricsService.updateAllUserContainerMetrics(userId);
            } else {
                // Update metrics asynchronously in background for next visit
                metricsService.updateAllUserContainerMetricsAsync(userId);
            }

            // Get dashboard statistics using current containers
            DashboardStats stats = dashboardService.getDashboardStats(userId);
            Map<String, List<Double>> usageHistory = dashboardService.getResourceUsageHistory(containers, 7);
            List<RecentActivity> recentActivity = dashboardService.getRecentActivity(userId, 3);
            
            // Add data to model
            model.addAttribute("username", user.getName());
            model.addAttribute("stats", stats);
            model.addAttribute("usageHistory", usageHistory);
            model.addAttribute("recentActivity", recentActivity);
            
            // Add chart data as JSON for JavaScript
            model.addAttribute("cpuData", usageHistory.get("cpu"));
            model.addAttribute("memoryData", usageHistory.get("memory"));
            model.addAttribute("networkInData", usageHistory.get("networkIn"));
            
            // Add notifications and user for notification bell
            List<Notification> notifications = dashboardService.getNotifications(userId, 3);
            model.addAttribute("notifications", notifications);
            model.addAttribute("notificationCount", notifications.size() + (user.getPlan() == User.UserPlan.FREE ? 1 : 0));
            model.addAttribute("user", user);
            model.addAttribute("networkOutData", usageHistory.get("networkOut"));
            
        } catch (Exception e) {
            log.error("Error loading dashboard", e);
            // Add default values to prevent template errors
            model.addAttribute("stats", DashboardStats.builder()
                .totalContainers(0L)
                .runningContainers(0L)
                .stoppedContainers(0L)
                .containerGrowth(0L)
                .cpuUsagePercent(0.0)
                .memoryUsageGB(0.0)
                .totalCpuVCores(0.0)
                .totalMemoryGB(0.0)
                .build());
        }
        
        return "dashboard";
    }

    

    @GetMapping("/containers")
    public String containers(Model model, Authentication authentication) {
        if (authentication == null) {
            return "redirect:/login";
        }

        try {
            String username = authentication.getName();
            User user = userService.findByEmail(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
            String userId = user.getUserId();

            // Update metrics for all user containers from CloudWatch
            metricsService.updateAllUserContainerMetrics(userId);

            // Get user's containers
            List<Container> containers = containerService.getUserContainers(userId);
            
            model.addAttribute("containers", containers);
            model.addAttribute("totalContainers", containers.size());
            model.addAttribute("runningContainers", 
                containers.stream().filter(c -> "RUNNING".equals(c.getStatus())).count());
            
        } catch (Exception e) {
            log.error("Error loading containers", e);
            model.addAttribute("containers", List.of());
            model.addAttribute("error", "Unable to load containers");
        }
        
        return "containers";
    }
    
    @GetMapping("/containers/{containerId}")
    public String containerDetails(@PathVariable String containerId, 
                                 Model model, 
                                 Authentication authentication) {
        if (authentication == null) {
            return "redirect:/login";
        }
        
        try {
            String username = authentication.getName();
            User user = userService.findByEmail(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            // Get container details
            Container container = containerService.getContainer(containerId);
            
            // Verify ownership
            if (!container.getUserId().equals(user.getUserId())) {
                return "redirect:/containers";
            }
            
            model.addAttribute("container", container);
            
        } catch (Exception e) {
            log.error("Error loading container details", e);
            return "redirect:/containers";
        }
        
        return "container-details";
    }
    
    @GetMapping("/deployments/{deploymentId}")
    public String deploymentDetails(@PathVariable String deploymentId, Model model, Authentication authentication) {
        if (authentication == null) {
            return "redirect:/login";
        }

        try {
            Deployment deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new RuntimeException("Deployment not found"));

            // Enrich with container name if missing
            if (deployment.getContainerName() == null && deployment.getContainerId() != null) {
                try {
                    Container container = containerService.getContainer(deployment.getContainerId());
                    if (container != null) {
                        deployment.setContainerName(container.getContainerName());
                    }
                } catch (Exception e) {
                    log.warn("Could not fetch container name for deployment: {}", deployment.getDeploymentId());
                }
            }

            model.addAttribute("deployment", deployment);
        } catch (Exception e) {
            log.error("Error loading deployment details", e);
            return "redirect:/deployments";
        }

        return "deployment-details";
    }

    @GetMapping("/deployments")
    public String deployments(Model model, Authentication authentication) {
        if (authentication == null) {
            return "redirect:/login";
        }

        try {
            String username = authentication.getName();
            User user = userService.findByEmail(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
            String userId = user.getUserId();

            List<Deployment> deployments = deploymentRepository.findRecentByUserId(userId, 50);

            // Enrich deployments with container names if missing
            for (Deployment deployment : deployments) {
                String containerName = deployment.getContainerName();
                if ((containerName == null || containerName.trim().isEmpty()) && deployment.getContainerId() != null) {
                    try {
                        Container container = containerService.getContainer(deployment.getContainerId());
                        if (container != null && container.getContainerName() != null) {
                            deployment.setContainerName(container.getContainerName());
                        } else {
                            deployment.setContainerName("Deleted Container");
                        }
                    } catch (Exception e) {
                        log.warn("Could not fetch container name for deployment: {}", deployment.getDeploymentId(), e);
                        deployment.setContainerName("Unknown");
                    }
                }
            }

            model.addAttribute("deployments", deployments);
        } catch (Exception e) {
            log.error("Error loading deployments", e);
            model.addAttribute("deployments", List.of());
        }

        return "deployments";
    }
    
    @GetMapping("/deploy")
    public String deployWizard(Model model, Authentication authentication) {
        if (authentication == null) {
            return "redirect:/login";
        }
        
        // Deployment wizard page
        return "deploy";
    }
    
    @GetMapping("/logs")
    public String logs(Model model, Authentication authentication) {
        if (authentication == null) {
            return "redirect:/login";
        }
        
        // Logs viewer page
        return "logs";
    }
    

    @GetMapping("/billing")
    public String billing(Model model, Authentication authentication) {
        if (authentication == null) {
            return "redirect:/login";
        }
        
        try {
            String username = authentication.getName();
            User user = userService.findByEmail(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            model.addAttribute("subscriptionPlan", user.getSubscriptionPlan());
            model.addAttribute("subscriptionStatus", user.getSubscriptionStatus());
            
        } catch (Exception e) {
            log.error("Error loading billing", e);
        }
        
        return "billing";
    }

    
    

    @GetMapping("/profile")
    public String profile(Model model, Authentication authentication) {
        if (authentication == null) {
            return "redirect:/login";
        }
        
        try {
            String username = authentication.getName();
            User user = userService.findByEmail(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            model.addAttribute("user", user);
            
        } catch (Exception e) {
            log.error("Error loading profile", e);
        }
        
        return "profile";
    }
    
    @GetMapping("/settings")
    public String settings(Model model, Authentication authentication) {
        if (authentication == null) {
            return "redirect:/login";
        }
        
        try {
            String username = authentication.getName();
            User user = userService.findByEmail(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            model.addAttribute("user", user);
            
        } catch (Exception e) {
            log.error("Error loading settings", e);
        }
        
        return "settings";
    }
    
    @GetMapping("/api-keys")
    public String apiKeys(Model model, Authentication authentication) {
        if (authentication == null) {
            return "redirect:/login";
        }
        
        try {
            String username = authentication.getName();
            User user = userService.findByEmail(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            model.addAttribute("apiKey", user.getApiKey());
            model.addAttribute("hasApiKey", user.getApiKey() != null);
            
        } catch (Exception e) {
            log.error("Error loading API keys", e);
        }
        
        return "api-keys";
    }
    
    
    @GetMapping("/support")
    public String support(Model model, Authentication authentication) {
        if (authentication == null) {
            return "redirect:/login";
        }
        
        return "support";
    }
    
    @GetMapping("/docs")
    public String documentation() {
        return "docs";
    }

    @GetMapping("/api")
    public String apiDocumentation() {
        return "api";
    }

    @GetMapping("/cli")
    public String cliInstallation(Model model, Authentication authentication) {
        if (authentication == null) {
            return "redirect:/login";
        }
        
        return "cli";
    }
    
    @GetMapping("/status")
    public String status(Model model) {
        // Public status page
        return "status";
    }
    
    @GetMapping("/domains")
    public String domains(Model model, Authentication authentication) {
        if (authentication == null) {
            return "redirect:/login";
        }

        // Domain management page
        return "domains";
    }

    // Compliance and Information Pages
    @GetMapping("/about")
    public String about() {
        return "about";
    }

    @GetMapping("/contact")
    public String contact() {
        return "contact";
    }

    @GetMapping("/terms")
    public String terms() {
        return "terms";
    }

    @GetMapping("/privacy")
    public String privacy() {
        return "privacy";
    }

    @GetMapping("/refund")
    public String refund() {
        return "refund";
    }

}
