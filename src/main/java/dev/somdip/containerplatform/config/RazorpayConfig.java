package dev.somdip.containerplatform.config;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RazorpayConfig {

    private static final Logger log = LoggerFactory.getLogger(RazorpayConfig.class);

    @Value("${razorpay.key.id}")
    private String keyId;

    @Value("${razorpay.key.secret}")
    private String keySecret;

    @Value("${razorpay.webhook.secret}")
    private String webhookSecret;

    @Value("${razorpay.currency:INR}")
    private String currency;

    @Value("${razorpay.plan.starter:}")
    private String starterPlanId;

    @Value("${razorpay.plan.pro:}")
    private String proPlanId;

    @Value("${razorpay.plan.business:}")
    private String businessPlanId;

    @Value("${razorpay.plan.enterprise:}")
    private String enterprisePlanId;

    @Value("${razorpay.pricing.starter:49900}")
    private int starterPrice;

    @Value("${razorpay.pricing.pro:149900}")
    private int proPrice;

    @Value("${razorpay.pricing.business:499900}")
    private int businessPrice;

    @Value("${razorpay.pricing.enterprise:999900}")
    private int enterprisePrice;

    private Map<String, String> planIds;
    private Map<String, Integer> planPrices;

    @Bean
    public RazorpayClient razorpayClient() throws RazorpayException {
        log.info("Initializing Razorpay client with key: {}...", keyId.substring(0, Math.min(10, keyId.length())));
        return new RazorpayClient(keyId, keySecret);
    }

    public String getKeyId() {
        return keyId;
    }

    public String getKeySecret() {
        return keySecret;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public String getCurrency() {
        return currency;
    }

    public String getPlanId(String planName) {
        if (planIds == null) {
            planIds = new HashMap<>();
            planIds.put("STARTER", starterPlanId);
            planIds.put("PRO", proPlanId);
            planIds.put("BUSINESS", businessPlanId);
            planIds.put("ENTERPRISE", enterprisePlanId);
        }
        return planIds.get(planName.toUpperCase());
    }

    public int getPriceInPaise(String planName) {
        if (planPrices == null) {
            planPrices = new HashMap<>();
            planPrices.put("STARTER", starterPrice);
            planPrices.put("PRO", proPrice);
            planPrices.put("BUSINESS", businessPrice);
            planPrices.put("ENTERPRISE", enterprisePrice);
        }
        return planPrices.getOrDefault(planName.toUpperCase(), 0);
    }

    /**
     * Calculate price based on plan and billing cycle
     * - Monthly: base price
     * - Quarterly: 3 months with 10% discount
     * - Annual: 12 months with ~17% discount (equivalent to ~10 months)
     */
    public int getPriceInPaise(String planName, String billingCycle) {
        int monthlyPrice = getPriceInPaise(planName);
        if (monthlyPrice == 0) {
            return 0;
        }

        if (billingCycle == null) {
            billingCycle = "monthly";
        }

        switch (billingCycle.toLowerCase()) {
            case "quarterly":
                // 3 months with 10% discount = 3 * 0.9 = 2.7x monthly
                return (int) (monthlyPrice * 3 * 0.9);
            case "annual":
                // 12 months with ~17% discount = 12 * 0.83 ≈ 9.96x monthly
                return (int) (monthlyPrice * 12 * 0.83);
            case "monthly":
            default:
                return monthlyPrice;
        }
    }
}
