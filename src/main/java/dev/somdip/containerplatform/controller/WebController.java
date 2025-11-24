package dev.somdip.containerplatform.controller;

import dev.somdip.containerplatform.dto.DashboardStats;
import dev.somdip.containerplatform.dto.DeploymentTimelineEvent;
import dev.somdip.containerplatform.dto.RecentActivity;
import dev.somdip.containerplatform.model.Container;
import dev.somdip.containerplatform.model.User;
import dev.somdip.containerplatform.security.CustomUserDetails;
import dev.somdip.containerplatform.service.ContainerService;
import dev.somdip.containerplatform.service.DashboardService;
import dev.somdip.containerplatform.service.UsageTrackingService;
import dev.somdip.containerplatform.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WebController {

    private final DashboardService dashboardService;
    private final ContainerService containerService;
    private final UserService userService;
    private final UsageTrackingService usageTrackingService;

    /**
     * Helper method to extract userId from Authentication
     */
    private String getUserId(Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return userDetails.getUserId();
    }

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

    @GetMapping("/dashboard")
    public String dashboard(Model model, Authentication authentication) {
        if (authentication == null) {
            return "redirect:/login";
        }
        
        try {
            // Get user ID from authentication
            String userId = getUserId(authentication);
            User user = userService.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            // Get dashboard statistics
            DashboardStats stats = dashboardService.getDashboardStats(userId);
            Map<String, List<Double>> usageHistory = dashboardService.getResourceUsageHistory(userId, 7);
            List<RecentActivity> recentActivity = dashboardService.getRecentActivity(userId, 5);
            Map<String, List<Double>> networkIO = dashboardService.getNetworkIOHistory(userId, 7);
            List<DeploymentTimelineEvent> deploymentTimeline = dashboardService.getDeploymentTimeline(userId, 7);

            // Calculate hours usage for FREE plan
            double hoursUsed = user.getHoursUsed() != null ? user.getHoursUsed() : 0.0;
            double hoursRemaining = usageTrackingService.getRemainingHours(user);
            boolean isFreePlan = user.getPlan() == User.UserPlan.FREE;
            boolean hasExceededLimit = isFreePlan && hoursRemaining <= 0;

            // Add data to model
            model.addAttribute("username", user.getName());
            model.addAttribute("stats", stats);
            model.addAttribute("usageHistory", usageHistory);
            model.addAttribute("recentActivity", recentActivity);
            model.addAttribute("networkIO", networkIO);
            model.addAttribute("deploymentTimeline", deploymentTimeline);

            // Add FREE plan usage data
            model.addAttribute("isFreePlan", isFreePlan);
            model.addAttribute("hoursUsed", hoursUsed);
            model.addAttribute("hoursRemaining", hoursRemaining);
            model.addAttribute("hoursLimit", UsageTrackingService.FREE_PLAN_HOURS_LIMIT);
            model.addAttribute("hasExceededLimit", hasExceededLimit);
            model.addAttribute("usagePercentage", isFreePlan ? (hoursUsed / UsageTrackingService.FREE_PLAN_HOURS_LIMIT) * 100 : 0);

            // Add chart data as JSON for JavaScript
            model.addAttribute("cpuData", usageHistory.get("cpu"));
            model.addAttribute("memoryData", usageHistory.get("memory"));
            model.addAttribute("networkInData", networkIO.get("in"));
            model.addAttribute("networkOutData", networkIO.get("out"));
            
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
            
            // Get user's containers
            List<Container> containers = containerService.getUserContainers(userId);
            
            model.addAttribute("containers", containers);
            model.addAttribute("totalContainers", containers.size());
            model.addAttribute("runningContainers",
                containers.stream().filter(c -> c.getStatus() == Container.ContainerStatus.RUNNING).count());
            
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
    
    @GetMapping("/deployments")
    public String deployments(Model model, Authentication authentication) {
        if (authentication == null) {
            return "redirect:/login";
        }
        
        // TODO: Implement deployments page
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
        // Redirect to documentation site or serve static docs
        return "redirect:/docs/index.html";
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

    @GetMapping("/metrics")
    public String metrics(Model model, Authentication authentication) {
        if (authentication == null) {
            return "redirect:/login";
        }

        // Metrics page
        return "metrics";
    }
}