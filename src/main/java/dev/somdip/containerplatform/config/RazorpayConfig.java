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
}
