package dev.somdip.containerplatform.service;

import dev.somdip.containerplatform.model.PaymentTransaction;
import dev.somdip.containerplatform.model.User;
import dev.somdip.containerplatform.repository.PaymentTransactionRepository;
import dev.somdip.containerplatform.repository.UserRepository;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class RazorpayWebhookService {

    private static final Logger log = LoggerFactory.getLogger(RazorpayWebhookService.class);

    private final PaymentTransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public RazorpayWebhookService(PaymentTransactionRepository transactionRepository,
                                   UserRepository userRepository) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    public void handleWebhookEvent(String eventType, JSONObject payload) {
        log.info("Processing webhook event: {}", eventType);

        switch (eventType) {
            case "payment.authorized":
                handlePaymentAuthorized(payload);
                break;
            case "payment.captured":
                handlePaymentCaptured(payload);
                break;
            case "payment.failed":
                handlePaymentFailed(payload);
                break;
            case "subscription.activated":
                handleSubscriptionActivated(payload);
                break;
            case "subscription.charged":
                handleSubscriptionCharged(payload);
                break;
            case "subscription.cancelled":
                handleSubscriptionCancelled(payload);
                break;
            case "subscription.halted":
                handleSubscriptionHalted(payload);
                break;
            case "refund.created":
                handleRefundCreated(payload);
                break;
            default:
                log.info("Unhandled webhook event type: {}", eventType);
        }
    }

    private void handlePaymentAuthorized(JSONObject payload) {
        JSONObject paymentEntity = payload.getJSONObject("payload").getJSONObject("payment").getJSONObject("entity");
        String paymentId = paymentEntity.getString("id");
        String orderId = paymentEntity.optString("order_id");

        log.info("Payment authorized: {} for order {}", paymentId, orderId);

        if (orderId != null && !orderId.isEmpty()) {
            transactionRepository.findByRazorpayOrderId(orderId).ifPresent(transaction -> {
                transaction.setRazorpayPaymentId(paymentId);
                transaction.setStatus(PaymentTransaction.TransactionStatus.AUTHORIZED);
                transactionRepository.save(transaction);
            });
        }
    }

    private void handlePaymentCaptured(JSONObject payload) {
        JSONObject paymentEntity = payload.getJSONObject("payload").getJSONObject("payment").getJSONObject("entity");
        String paymentId = paymentEntity.getString("id");
        String orderId = paymentEntity.optString("order_id");

        log.info("Payment captured: {} for order {}", paymentId, orderId);

        if (orderId != null && !orderId.isEmpty()) {
            transactionRepository.findByRazorpayOrderId(orderId).ifPresent(transaction -> {
                transaction.setRazorpayPaymentId(paymentId);
                transaction.setStatus(PaymentTransaction.TransactionStatus.CAPTURED);
                transaction.setPaidAt(Instant.now());
                transaction.setPaymentMethod(paymentEntity.optString("method"));
                transactionRepository.save(transaction);

                // Update user plan
                updateUserPlanFromTransaction(transaction);
            });
        }
    }

    private void handlePaymentFailed(JSONObject payload) {
        JSONObject paymentEntity = payload.getJSONObject("payload").getJSONObject("payment").getJSONObject("entity");
        String paymentId = paymentEntity.getString("id");
        String orderId = paymentEntity.optString("order_id");

        log.error("Payment failed: {} for order {}", paymentId, orderId);

        if (orderId != null && !orderId.isEmpty()) {
            transactionRepository.findByRazorpayOrderId(orderId).ifPresent(transaction -> {
                transaction.setRazorpayPaymentId(paymentId);
                transaction.setStatus(PaymentTransaction.TransactionStatus.FAILED);
                transaction.setErrorCode(paymentEntity.optString("error_code"));
                transaction.setErrorDescription(paymentEntity.optString("error_description"));
                transactionRepository.save(transaction);
            });
        }
    }

    private void handleSubscriptionActivated(JSONObject payload) {
        JSONObject subscriptionEntity = payload.getJSONObject("payload").getJSONObject("subscription").getJSONObject("entity");
        String subscriptionId = subscriptionEntity.getString("id");
        String customerId = subscriptionEntity.optString("customer_id");
        JSONObject notes = subscriptionEntity.optJSONObject("notes");

        log.info("Subscription activated: {}", subscriptionId);

        if (notes != null) {
            String userId = notes.optString("userId");
            if (userId != null && !userId.isEmpty()) {
                userRepository.findById(userId).ifPresent(user -> {
                    user.setRazorpaySubscriptionId(subscriptionId);
                    user.setPaymentStatus(User.PaymentStatus.ACTIVE);
                    user.setSubscriptionStartDate(Instant.now());
                    userRepository.save(user);
                });
            }
        }
    }

    private void handleSubscriptionCharged(JSONObject payload) {
        JSONObject subscriptionEntity = payload.getJSONObject("payload").getJSONObject("subscription").getJSONObject("entity");
        String subscriptionId = subscriptionEntity.getString("id");
        JSONObject notes = subscriptionEntity.optJSONObject("notes");

        log.info("Subscription charged: {}", subscriptionId);

        if (notes != null) {
            String userId = notes.optString("userId");
            if (userId != null && !userId.isEmpty()) {
                userRepository.findById(userId).ifPresent(user -> {
                    user.setPaymentStatus(User.PaymentStatus.ACTIVE);
                    user.setNextBillingDate(Instant.now().plusSeconds(30L * 24 * 60 * 60)); // ~30 days
                    userRepository.save(user);
                });
            }
        }
    }

    private void handleSubscriptionCancelled(JSONObject payload) {
        JSONObject subscriptionEntity = payload.getJSONObject("payload").getJSONObject("subscription").getJSONObject("entity");
        String subscriptionId = subscriptionEntity.getString("id");
        JSONObject notes = subscriptionEntity.optJSONObject("notes");

        log.info("Subscription cancelled: {}", subscriptionId);

        if (notes != null) {
            String userId = notes.optString("userId");
            if (userId != null && !userId.isEmpty()) {
                userRepository.findById(userId).ifPresent(user -> {
                    user.setPaymentStatus(User.PaymentStatus.CANCELLED);
                    userRepository.save(user);
                });
            }
        }
    }

    private void handleSubscriptionHalted(JSONObject payload) {
        JSONObject subscriptionEntity = payload.getJSONObject("payload").getJSONObject("subscription").getJSONObject("entity");
        String subscriptionId = subscriptionEntity.getString("id");
        JSONObject notes = subscriptionEntity.optJSONObject("notes");

        log.warn("Subscription halted: {}", subscriptionId);

        if (notes != null) {
            String userId = notes.optString("userId");
            if (userId != null && !userId.isEmpty()) {
                userRepository.findById(userId).ifPresent(user -> {
                    user.setPaymentStatus(User.PaymentStatus.HALTED);
                    userRepository.save(user);
                });
            }
        }
    }

    private void handleRefundCreated(JSONObject payload) {
        JSONObject refundEntity = payload.getJSONObject("payload").getJSONObject("refund").getJSONObject("entity");
        String refundId = refundEntity.getString("id");
        String paymentId = refundEntity.getString("payment_id");
        Long amount = refundEntity.getLong("amount");

        log.info("Refund created: {} for payment {} - amount {}", refundId, paymentId, amount);

        transactionRepository.findByRazorpayPaymentId(paymentId).ifPresent(transaction -> {
            transaction.setRefundId(refundId);
            transaction.setRefundAmount(amount);
            if (amount.equals(transaction.getAmount())) {
                transaction.setStatus(PaymentTransaction.TransactionStatus.REFUNDED);
            } else {
                transaction.setStatus(PaymentTransaction.TransactionStatus.PARTIALLY_REFUNDED);
            }
            transactionRepository.save(transaction);
        });
    }

    private void updateUserPlanFromTransaction(PaymentTransaction transaction) {
        userRepository.findById(transaction.getUserId()).ifPresent(user -> {
            try {
                User.UserPlan newPlan = User.UserPlan.valueOf(transaction.getPlanName().toUpperCase());
                user.setPlan(newPlan);
                user.setPaymentStatus(User.PaymentStatus.ACTIVE);
                user.setSubscriptionStartDate(Instant.now());
                user.setLastAmountPaid(transaction.getAmount());
                userRepository.save(user);
                log.info("Updated user {} plan to {}", user.getUserId(), newPlan);
            } catch (IllegalArgumentException e) {
                log.error("Invalid plan name in transaction: {}", transaction.getPlanName());
            }
        });
    }
}
