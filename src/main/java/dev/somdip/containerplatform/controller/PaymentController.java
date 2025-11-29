package dev.somdip.containerplatform.controller;

import dev.somdip.containerplatform.dto.payment.*;
import dev.somdip.containerplatform.model.PaymentTransaction;
import dev.somdip.containerplatform.repository.PaymentTransactionRepository;
import dev.somdip.containerplatform.security.CustomUserDetails;
import dev.somdip.containerplatform.service.RazorpayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final RazorpayService razorpayService;
    private final PaymentTransactionRepository transactionRepository;

    public PaymentController(RazorpayService razorpayService,
                            PaymentTransactionRepository transactionRepository) {
        this.razorpayService = razorpayService;
        this.transactionRepository = transactionRepository;
    }

    @PostMapping("/orders")
    public ResponseEntity<CreateOrderResponse> createOrder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody CreateOrderRequest request) {
        try {
            String userId = userDetails.getUserId();
            CreateOrderResponse response = razorpayService.createOrder(userId, request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Invalid order request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error creating order: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyPayment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody VerifyPaymentRequest request) {
        try {
            String userId = userDetails.getUserId();
            PaymentTransaction transaction = razorpayService.verifyAndCapturePayment(userId, request);

            Map<String, Object> response = new HashMap<>();
            response.put("success", transaction.getStatus() == PaymentTransaction.TransactionStatus.CAPTURED);
            response.put("transactionId", transaction.getTransactionId());
            response.put("status", transaction.getStatus().name());
            response.put("planName", transaction.getPlanName());

            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            log.error("Payment verification failed: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Payment verification failed");
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("Error verifying payment: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/subscriptions")
    public ResponseEntity<SubscriptionResponse> createSubscription(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody CreateSubscriptionRequest request) {
        try {
            String userId = userDetails.getUserId();
            SubscriptionResponse response = razorpayService.createSubscription(userId, request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Invalid subscription request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error creating subscription: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/subscriptions/cancel")
    public ResponseEntity<Map<String, Object>> cancelSubscription(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        try {
            String userId = userDetails.getUserId();
            razorpayService.cancelSubscription(userId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Subscription cancellation initiated");

            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            log.error("Cannot cancel subscription: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("Error cancelling subscription: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/history")
    public ResponseEntity<List<PaymentTransaction>> getPaymentHistory(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        try {
            String userId = userDetails.getUserId();
            List<PaymentTransaction> transactions = transactionRepository.findByUserId(userId);
            return ResponseEntity.ok(transactions);
        } catch (Exception e) {
            log.error("Error fetching payment history: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/refunds")
    public ResponseEntity<Map<String, Object>> requestRefund(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam String transactionId,
            @RequestParam(required = false) Long amount,
            @RequestParam(required = false) String reason) {
        try {
            String userId = userDetails.getUserId();

            // Verify transaction belongs to user
            PaymentTransaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));

            if (!transaction.getUserId().equals(userId)) {
                throw new SecurityException("Transaction does not belong to user");
            }

            PaymentTransaction refundedTransaction = razorpayService.processRefund(transactionId, amount, reason);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("refundId", refundedTransaction.getRefundId());
            response.put("refundAmount", refundedTransaction.getRefundAmount());

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Cannot process refund: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (SecurityException e) {
            log.error("Unauthorized refund request: {}", e.getMessage());
            return ResponseEntity.status(403).build();
        } catch (Exception e) {
            log.error("Error processing refund: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
