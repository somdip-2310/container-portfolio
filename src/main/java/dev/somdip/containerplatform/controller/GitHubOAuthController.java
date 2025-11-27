package dev.somdip.containerplatform.controller;

import dev.somdip.containerplatform.model.GitHubConnection;
import dev.somdip.containerplatform.security.CustomUserDetails;
import dev.somdip.containerplatform.service.GitHubOAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/auth/github")
public class GitHubOAuthController {
    private static final Logger log = LoggerFactory.getLogger(GitHubOAuthController.class);

    private final GitHubOAuthService oAuthService;

    public GitHubOAuthController(GitHubOAuthService oAuthService) {
        this.oAuthService = oAuthService;
    }

    /**
     * Initiate GitHub OAuth flow
     * GET /auth/github/connect
     */
    @GetMapping("/connect")
    public RedirectView connect(Authentication authentication) {
        if (!oAuthService.isConfigured()) {
            log.warn("GitHub OAuth not configured");
            return new RedirectView("/dashboard/settings?github_error=not_configured");
        }

        String userId = getUserId(authentication);
        String authUrl = oAuthService.getAuthorizationUrl(userId);

        log.info("Initiating GitHub OAuth for user: {}", userId);
        return new RedirectView(authUrl);
    }

    /**
     * OAuth callback from GitHub
     * GET /auth/github/callback?code=xxx&state=xxx
     */
    @GetMapping("/callback")
    public RedirectView callback(
            @RequestParam("code") String code,
            @RequestParam("state") String state,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "error_description", required = false) String errorDesc) {

        if (error != null) {
            log.error("GitHub OAuth error: {} - {}", error, errorDesc);
            return new RedirectView("/dashboard/settings?github_error=" + error);
        }

        try {
            String userId = oAuthService.parseState(state);
            GitHubConnection connection = oAuthService.exchangeCodeForToken(code, userId);

            log.info("GitHub connected successfully for user: {}, GitHub: {}",
                userId, connection.getGithubUsername());
            return new RedirectView("/dashboard/settings?github_connected=true");

        } catch (Exception e) {
            log.error("GitHub OAuth callback failed", e);
            return new RedirectView("/dashboard/settings?github_error=callback_failed");
        }
    }

    /**
     * Disconnect GitHub
     * POST /auth/github/disconnect
     */
    @PostMapping("/disconnect")
    @ResponseBody
    public ResponseEntity<Map<String, String>> disconnect(Authentication authentication) {
        String userId = getUserId(authentication);

        try {
            oAuthService.revokeConnection(userId);
            return ResponseEntity.ok(Map.of("status", "disconnected"));
        } catch (Exception e) {
            log.error("Failed to disconnect GitHub", e);
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Check GitHub connection status
     * GET /auth/github/status
     */
    @GetMapping("/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> status(Authentication authentication) {
        String userId = getUserId(authentication);
        boolean connected = oAuthService.isConnected(userId);

        Map<String, Object> response = new HashMap<>();
        response.put("connected", connected);
        response.put("configured", oAuthService.isConfigured());

        if (connected) {
            oAuthService.getConnection(userId).ifPresent(conn -> {
                response.put("githubUsername", conn.getGithubUsername());
                response.put("avatarUrl", conn.getAvatarUrl());
            });
        }

        return ResponseEntity.ok(response);
    }

    private String getUserId(Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return userDetails.getUserId();
    }
}
