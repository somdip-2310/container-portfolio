package dev.somdip.containerplatform.controller;

import dev.somdip.containerplatform.service.RazorpayService;
import dev.somdip.containerplatform.service.RazorpayWebhookService;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhooks/razorpay")
public class RazorpayWebhookController {

    private static final Logger log = LoggerFactory.getLogger(RazorpayWebhookController.class);

    private final RazorpayService razorpayService;
    private final RazorpayWebhookService webhookService;

    public RazorpayWebhookController(RazorpayService razorpayService,
                                      RazorpayWebhookService webhookService) {
        this.razorpayService = razorpayService;
        this.webhookService = webhookService;
    }

    @PostMapping
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {

        log.info("Received Razorpay webhook");

        // Verify signature if provided
        if (signature != null && !signature.isEmpty()) {
            if (!razorpayService.verifyWebhookSignature(payload, signature)) {
                log.error("Webhook signature verification failed");
                return ResponseEntity.status(401).body("Invalid signature");
            }
        }

        try {
            JSONObject webhookPayload = new JSONObject(payload);
            String eventType = webhookPayload.getString("event");

            log.info("Processing webhook event: {}", eventType);

            webhookService.handleWebhookEvent(eventType, webhookPayload);

            return ResponseEntity.ok("Webhook processed");

        } catch (Exception e) {
            log.error("Error processing webhook: {}", e.getMessage(), e);
            // Return 200 to prevent retries for parsing errors
            return ResponseEntity.ok("Webhook received");
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Razorpay webhook endpoint is healthy");
    }
}
