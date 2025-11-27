package dev.somdip.containerplatform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GitHubConfig {

    @Value("${github.oauth.client-id:}")
    private String clientId;

    @Value("${github.oauth.client-secret:}")
    private String clientSecret;

    @Value("${github.oauth.redirect-uri:https://platform.somdip.dev/auth/github/callback}")
    private String redirectUri;

    @Value("${github.oauth.scope:repo,read:user,user:email}")
    private String scope;

    @Value("${github.api.base-url:https://api.github.com}")
    private String apiBaseUrl;

    @Value("${github.webhook.secret:}")
    private String webhookSecret;

    @Value("${github.webhook.url:https://platform.somdip.dev/webhooks/github}")
    private String webhookUrl;

    // Getters
    public String getClientId() { return clientId; }
    public String getClientSecret() { return clientSecret; }
    public String getRedirectUri() { return redirectUri; }
    public String getScope() { return scope; }
    public String getApiBaseUrl() { return apiBaseUrl; }
    public String getWebhookSecret() { return webhookSecret; }
    public String getWebhookUrl() { return webhookUrl; }

    public String getAuthorizationUrl() {
        return String.format(
            "https://github.com/login/oauth/authorize?client_id=%s&redirect_uri=%s&scope=%s&state=",
            clientId, redirectUri, scope
        );
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.isEmpty() &&
               clientSecret != null && !clientSecret.isEmpty();
    }
}
