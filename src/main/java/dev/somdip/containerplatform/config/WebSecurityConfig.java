package dev.somdip.containerplatform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class WebSecurityConfig {

    @Bean
    public SecurityHeadersConfigurer securityHeadersConfigurer() {
        return new SecurityHeadersConfigurer();
    }

    public static class SecurityHeadersConfigurer {
        public void configure(HttpSecurity http) throws Exception {
            http.headers(headers -> headers
                .frameOptions(frameOptions -> frameOptions.sameOrigin())
                .xssProtection(xss -> xss.disable()) // X-XSS-Protection is deprecated
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives("default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline';"))
                .addHeaderWriter(new StaticHeadersWriter("X-Content-Type-Options", "nosniff"))
                .addHeaderWriter(new StaticHeadersWriter("Referrer-Policy", "strict-origin-when-cross-origin"))
                .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy", "geolocation=(), microphone=(), camera=()"))
            );
        }
    }

    // HTTPS redirect configuration for production
    @Bean
    public HttpsRedirectConfigurer httpsRedirectConfigurer() {
        return new HttpsRedirectConfigurer();
    }

    public static class HttpsRedirectConfigurer {
        public void configure(HttpSecurity http) throws Exception {
            http.requiresChannel(channel -> channel
                .requestMatchers(r -> r.getHeader("X-Forwarded-Proto") != null)
                .requiresSecure()
            );
        }
    }
}