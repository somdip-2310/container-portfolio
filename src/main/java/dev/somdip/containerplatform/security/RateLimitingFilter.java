package dev.somdip.containerplatform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    @Value("${app.rateLimit.enabled:true}")
    private boolean rateLimitEnabled;

    @Value("${app.rateLimit.requests.perMinute:60}")
    private int requestsPerMinute;

    @Value("${app.rateLimit.requests.perHour:1000}")
    private int requestsPerHour;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        if (!rateLimitEnabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = getClientKey(request);
        Bucket bucket = resolveBucket(key);

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            handleRateLimitExceeded(response);
        }
    }

    private Bucket resolveBucket(String key) {
        return buckets.computeIfAbsent(key, k -> createNewBucket());
    }

    private Bucket createNewBucket() {
        Bandwidth minuteLimit = Bandwidth.classic(requestsPerMinute, 
            Refill.intervally(requestsPerMinute, Duration.ofMinutes(1)));
        Bandwidth hourLimit = Bandwidth.classic(requestsPerHour, 
            Refill.intervally(requestsPerHour, Duration.ofHours(1)));
        
        return Bucket.builder()
            .addLimit(minuteLimit)
            .addLimit(hourLimit)
            .build();
    }

    private String getClientKey(HttpServletRequest request) {
        // Try to get authenticated user ID first
        if (request.getUserPrincipal() != null) {
            return "user:" + request.getUserPrincipal().getName();
        }

        // Try to get API key
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey != null && !apiKey.isEmpty()) {
            return "api:" + apiKey;
        }

        // Fall back to IP address
        String clientIp = request.getHeader("X-Forwarded-For");
        if (clientIp == null || clientIp.isEmpty()) {
            clientIp = request.getRemoteAddr();
        }
        return "ip:" + clientIp;
    }

    private void handleRateLimitExceeded(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        errorDetails.put("error", "Too Many Requests");
        errorDetails.put("message", "Rate limit exceeded. Please try again later.");

        objectMapper.writeValue(response.getOutputStream(), errorDetails);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        
        // Exclude these paths from rate limiting
        return path.equals("/") ||
               path.startsWith("/health") || 
               path.startsWith("/static") || 
               path.startsWith("/css") || 
               path.startsWith("/js") ||
               path.startsWith("/favicon.ico") ||
               path.equals("/login") ||
               path.equals("/register") ||
               path.startsWith("/api/auth/");  // Allow auth endpoints without rate limiting
    }
}