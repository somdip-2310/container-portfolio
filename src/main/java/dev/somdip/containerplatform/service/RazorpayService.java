package dev.somdip.containerplatform.service;

import com.razorpay.*;
import dev.somdip.containerplatform.config.RazorpayConfig;
import dev.somdip.containerplatform.dto.payment.*;
import dev.somdip.containerplatform.model.PaymentTransaction;
import dev.somdip.containerplatform.model.User;
import dev.somdip.containerplatform.repository.PaymentTransactionRepository;
import dev.somdip.containerplatform.repository.UserRepository;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class RazorpayService {

    private static final Logger log = LoggerFactory.getLogger(RazorpayService.class);

    private final RazorpayClient razorpayClient;
    private final RazorpayConfig razorpayConfig;
    private final PaymentTransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public RazorpayService(RazorpayClient razorpayClient,
                          RazorpayConfig razorpayConfig,
                          PaymentTransactionRepository transactionRepository,
                          UserRepository userRepository) {
        this.razorpayClient = razorpayClient;
        this.razorpayConfig = razorpayConfig;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    /**
     * Create a Razorpay order for one-time payment (plan upgrade)
     */
    public CreateOrderResponse createOrder(String userId, CreateOrderRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String planName = request.getPlanName().toUpperCase();
        int amount = razorpayConfig.getPriceInPaise(planName);
        String currency = request.getCurrency() != null ? request.getCurrency() : razorpayConfig.getCurrency();

        if (amount == 0) {
            throw new IllegalArgumentException("Invalid plan: " + planName);
        }

        try {
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amount);
            orderRequest.put("currency", currency);
            orderRequest.put("receipt", "order_" + UUID.randomUUID().toString().substring(0, 8));

            JSONObject notes = new JSONObject();
            notes.put("userId", userId);
            notes.put("planName", planName);
            notes.put("billingCycle", request.getBillingCycle() != null ? request.getBillingCycle() : "monthly");
            orderRequest.put("notes", notes);

            Order order = razorpayClient.orders.create(orderRequest);

            // Create transaction record
            PaymentTransaction transaction = new PaymentTransaction();
            transaction.setUserId(userId);
            transaction.setRazorpayOrderId(order.get("id"));
            transaction.setType(PaymentTransaction.TransactionType.SUBSCRIPTION);
            transaction.setStatus(PaymentTransaction.TransactionStatus.CREATED);
            transaction.setAmount((long) amount);
            transaction.setCurrency(currency);
            transaction.setPlanName(planName);
            transaction.setDescription(planName + " Plan - " + (request.getBillingCycle() != null ? request.getBillingCycle() : "Monthly"));

            Map<String, String> metadata = new HashMap<>();
            metadata.put("billingCycle", request.getBillingCycle() != null ? request.getBillingCycle() : "monthly");
            transaction.setMetadata(metadata);

            transactionRepository.save(transaction);

            // Build response
            CreateOrderResponse response = new CreateOrderResponse();
            response.setOrderId(transaction.getTransactionId());
            response.setRazorpayOrderId(order.get("id"));
            response.setAmount((long) amount);
            response.setCurrency(currency);
            response.setPlanName(planName);
            response.setRazorpayKeyId(razorpayConfig.getKeyId());
            response.setCustomerName(user.getName());
            response.setCustomerEmail(user.getEmail());
            response.setDescription(planName + " Plan Subscription");

            log.info("Created Razorpay order {} for user {} - plan {}", order.get("id"), userId, planName);
            return response;

        } catch (RazorpayException e) {
            log.error("Error creating Razorpay order: {}", e.getMessage());
            throw new RuntimeException("Failed to create payment order: " + e.getMessage(), e);
        }
    }

    /**
     * Verify payment signature and complete the payment
     */
    public PaymentTransaction verifyAndCapturePayment(String userId, VerifyPaymentRequest request) {
        // Verify signature
        String generatedSignature = generateSignature(
            request.getRazorpayOrderId() + "|" + request.getRazorpayPaymentId(),
            razorpayConfig.getKeySecret()
        );

        if (!generatedSignature.equals(request.getRazorpaySignature())) {
            log.error("Signature verification failed for payment {}", request.getRazorpayPaymentId());
            throw new SecurityException("Payment signature verification failed");
        }

        // Find transaction
        PaymentTransaction transaction = transactionRepository.findByRazorpayOrderId(request.getRazorpayOrderId())
            .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));

        // Verify user
        if (!transaction.getUserId().equals(userId)) {
            throw new SecurityException("Transaction does not belong to user");
        }

        try {
            // Fetch payment details from Razorpay
            Payment payment = razorpayClient.payments.fetch(request.getRazorpayPaymentId());

            // Update transaction
            transaction.setRazorpayPaymentId(request.getRazorpayPaymentId());
            transaction.setRazorpaySignature(request.getRazorpaySignature());
            transaction.setPaymentMethod(payment.get("method"));

            if ("card".equals(payment.get("method"))) {
                JSONObject card = payment.get("card");
                if (card != null) {
                    transaction.setCardLast4(card.optString("last4"));
                }
            } else if ("netbanking".equals(payment.get("method"))) {
                transaction.setBankName(payment.get("bank"));
            }

            String paymentStatus = payment.get("status");
            if ("captured".equals(paymentStatus)) {
                transaction.setStatus(PaymentTransaction.TransactionStatus.CAPTURED);
                transaction.setPaidAt(Instant.now());

                // Update user's plan
                updateUserPlan(userId, transaction.getPlanName());

                log.info("Payment captured successfully for user {} - plan {}", userId, transaction.getPlanName());
            } else if ("authorized".equals(paymentStatus)) {
                transaction.setStatus(PaymentTransaction.TransactionStatus.AUTHORIZED);
            } else {
                transaction.setStatus(PaymentTransaction.TransactionStatus.FAILED);
                transaction.setErrorCode(payment.get("error_code"));
                transaction.setErrorDescription(payment.get("error_description"));
            }

            return transactionRepository.save(transaction);

        } catch (RazorpayException e) {
            log.error("Error verifying payment: {}", e.getMessage());
            transaction.setStatus(PaymentTransaction.TransactionStatus.FAILED);
            transaction.setErrorDescription(e.getMessage());
            transactionRepository.save(transaction);
            throw new RuntimeException("Failed to verify payment: " + e.getMessage(), e);
        }
    }

    /**
     * Create or get Razorpay customer ID
     */
    public String getOrCreateCustomerId(String userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.getRazorpayCustomerId() != null) {
            return user.getRazorpayCustomerId();
        }

        try {
            JSONObject customerRequest = new JSONObject();
            customerRequest.put("name", user.getName());
            customerRequest.put("email", user.getEmail());
            customerRequest.put("fail_existing", "0"); // Return existing if found

            Customer customer = razorpayClient.customers.create(customerRequest);
            String customerId = customer.get("id");

            user.setRazorpayCustomerId(customerId);
            userRepository.save(user);

            log.info("Created Razorpay customer {} for user {}", customerId, userId);
            return customerId;

        } catch (RazorpayException e) {
            log.error("Error creating Razorpay customer: {}", e.getMessage());
            throw new RuntimeException("Failed to create customer: " + e.getMessage(), e);
        }
    }

    /**
     * Create a subscription
     */
    public SubscriptionResponse createSubscription(String userId, CreateSubscriptionRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String planName = request.getPlanName().toUpperCase();
        String planId = razorpayConfig.getPlanId(planName);

        if (planId == null || planId.isEmpty()) {
            throw new IllegalArgumentException("Plan not configured: " + planName);
        }

        String customerId = getOrCreateCustomerId(userId);

        try {
            JSONObject subscriptionRequest = new JSONObject();
            subscriptionRequest.put("plan_id", planId);
            subscriptionRequest.put("customer_id", customerId);
            subscriptionRequest.put("quantity", 1);
            subscriptionRequest.put("total_count", request.getTotalCount() != null ? request.getTotalCount() : 12);
            subscriptionRequest.put("customer_notify", 1);

            JSONObject notes = new JSONObject();
            notes.put("userId", userId);
            notes.put("planName", planName);
            subscriptionRequest.put("notes", notes);

            Subscription subscription = razorpayClient.subscriptions.create(subscriptionRequest);

            // Update user
            user.setRazorpaySubscriptionId(subscription.get("id"));
            user.setBillingCycle(request.getBillingCycle() != null ? request.getBillingCycle() : "monthly");
            user.setPaymentStatus(User.PaymentStatus.CREATED);
            userRepository.save(user);

            // Build response
            SubscriptionResponse response = new SubscriptionResponse();
            response.setSubscriptionId(user.getUserId());
            response.setRazorpaySubscriptionId(subscription.get("id"));
            response.setPlanName(planName);
            response.setStatus(subscription.get("status"));
            response.setShortUrl(subscription.get("short_url"));
            response.setRazorpayKeyId(razorpayConfig.getKeyId());
            response.setAmount((long) razorpayConfig.getPriceInPaise(planName));
            response.setCurrency(razorpayConfig.getCurrency());
            response.setBillingCycle(request.getBillingCycle() != null ? request.getBillingCycle() : "monthly");

            log.info("Created subscription {} for user {} - plan {}", subscription.get("id"), userId, planName);
            return response;

        } catch (RazorpayException e) {
            log.error("Error creating subscription: {}", e.getMessage());
            throw new RuntimeException("Failed to create subscription: " + e.getMessage(), e);
        }
    }

    /**
     * Cancel subscription
     */
    public void cancelSubscription(String userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.getRazorpaySubscriptionId() == null) {
            throw new IllegalStateException("No active subscription found");
        }

        try {
            JSONObject cancelRequest = new JSONObject();
            cancelRequest.put("cancel_at_cycle_end", 1); // Cancel at end of billing cycle

            razorpayClient.subscriptions.cancel(user.getRazorpaySubscriptionId(), cancelRequest);

            user.setPaymentStatus(User.PaymentStatus.CANCELLED);
            userRepository.save(user);

            log.info("Cancelled subscription for user {}", userId);

        } catch (RazorpayException e) {
            log.error("Error cancelling subscription: {}", e.getMessage());
            throw new RuntimeException("Failed to cancel subscription: " + e.getMessage(), e);
        }
    }

    /**
     * Process refund
     */
    public PaymentTransaction processRefund(String transactionId, Long amount, String reason) {
        PaymentTransaction transaction = transactionRepository.findById(transactionId)
            .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));

        if (transaction.getRazorpayPaymentId() == null) {
            throw new IllegalStateException("No payment ID associated with transaction");
        }

        try {
            JSONObject refundRequest = new JSONObject();
            if (amount != null) {
                refundRequest.put("amount", amount);
            }
            if (reason != null) {
                JSONObject notes = new JSONObject();
                notes.put("reason", reason);
                refundRequest.put("notes", notes);
            }

            Refund refund = razorpayClient.payments.refund(transaction.getRazorpayPaymentId(), refundRequest);

            transaction.setRefundId(refund.get("id"));
            transaction.setRefundAmount(Long.valueOf(refund.get("amount").toString()));
            transaction.setRefundReason(reason);
            transaction.setStatus(
                refund.get("amount").equals(transaction.getAmount())
                    ? PaymentTransaction.TransactionStatus.REFUNDED
                    : PaymentTransaction.TransactionStatus.PARTIALLY_REFUNDED
            );

            log.info("Processed refund {} for transaction {}", refund.get("id"), transactionId);
            return transactionRepository.save(transaction);

        } catch (RazorpayException e) {
            log.error("Error processing refund: {}", e.getMessage());
            throw new RuntimeException("Failed to process refund: " + e.getMessage(), e);
        }
    }

    /**
     * Update user's plan after successful payment
     */
    private void updateUserPlan(String userId, String planName) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        User.UserPlan newPlan = User.UserPlan.valueOf(planName.toUpperCase());
        user.setPlan(newPlan);
        user.setPaymentStatus(User.PaymentStatus.ACTIVE);
        user.setSubscriptionStartDate(Instant.now());
        user.setLastAmountPaid((long) razorpayConfig.getPriceInPaise(planName));

        userRepository.save(user);
        log.info("Updated user {} plan to {}", userId, planName);
    }

    /**
     * Generate HMAC SHA256 signature for verification
     */
    private String generateSignature(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(data.getBytes());

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error generating signature", e);
        }
    }

    /**
     * Verify webhook signature
     */
    public boolean verifyWebhookSignature(String payload, String signature) {
        String expectedSignature = generateSignature(payload, razorpayConfig.getWebhookSecret());
        return expectedSignature.equals(signature);
    }
}
