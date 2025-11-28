package dev.somdip.containerplatform.controller;

import dev.somdip.containerplatform.config.GitHubConfig;
import dev.somdip.containerplatform.service.GitHubWebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/webhooks/github")
public class GitHubWebhookController {
    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookController.class);

    private final GitHubWebhookService webhookService;
    private final GitHubConfig gitHubConfig;

    public GitHubWebhookController(GitHubWebhookService webhookService,
                                   GitHubConfig gitHubConfig) {
        this.webhookService = webhookService;
        this.gitHubConfig = gitHubConfig;
    }

    /**
     * Receive webhook events from GitHub
     * POST /webhooks/github
     */
    @PostMapping
    public ResponseEntity<String> handleWebhook(
            @RequestHeader("X-GitHub-Event") String event,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader("X-GitHub-Delivery") String deliveryId,
            @RequestBody String payload) {

        log.info("Received GitHub webhook: event={}, deliveryId={}", event, deliveryId);

        // Validate signature if webhook secret is configured
        String webhookSecret = gitHubConfig.getWebhookSecret();
        if (webhookSecret != null && !webhookSecret.isEmpty()) {
            if (signature == null || !validateSignature(payload, signature, webhookSecret)) {
                log.warn("Invalid webhook signature for delivery: {}", deliveryId);
                return ResponseEntity.status(401).body("Invalid signature");
            }
        }

        try {
            switch (event) {
                case "push":
                    webhookService.handlePushEvent(payload);
                    break;
                case "pull_request":
                    webhookService.handlePullRequestEvent(payload);
                    break;
                case "ping":
                    log.info("Webhook ping received, deliveryId={}", deliveryId);
                    break;
                default:
                    log.debug("Ignoring event type: {}", event);
            }

            return ResponseEntity.ok("OK");

        } catch (Exception e) {
            log.error("Error processing webhook", e);
            return ResponseEntity.status(500).body("Processing error");
        }
    }

    /**
     * Validate webhook signature using HMAC SHA-256
     */
    private boolean validateSignature(String payload, String signature, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);

            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = "sha256=" + bytesToHex(hash);

            return expectedSignature.equals(signature);
        } catch (Exception e) {
            log.error("Signature validation failed", e);
            return false;
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
