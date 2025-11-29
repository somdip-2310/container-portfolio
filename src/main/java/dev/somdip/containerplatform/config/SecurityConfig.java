package dev.somdip.containerplatform.config;

import dev.somdip.containerplatform.security.ApiKeyAuthenticationFilter;
import dev.somdip.containerplatform.security.CustomUserDetailsService;
import dev.somdip.containerplatform.security.JwtAuthenticationFilter;
import dev.somdip.containerplatform.security.JwtAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final CustomUserDetailsService userDetailsService;

    public SecurityConfig(JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
                         CustomUserDetailsService userDetailsService) {
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain apiFilterChain(HttpSecurity http,
                                             JwtAuthenticationFilter jwtAuthenticationFilter,
                                             ApiKeyAuthenticationFilter apiKeyAuthenticationFilter) throws Exception {
        http
            // Note: /api/github/** and /api/payments/** removed - they use session auth via webFilterChain for browser requests
            .securityMatcher("/api/containers/**", "/api/metrics/**", "/api/logs/**", "/api/auth/**", "/api/health/**", "/webhooks/**")
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(jwtAuthenticationEntryPoint))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/health/**").permitAll()
                .requestMatchers("/webhooks/github").permitAll()  // GitHub webhooks
                .requestMatchers("/webhooks/razorpay").permitAll()  // Razorpay payment webhooks
                .requestMatchers("/api/**").authenticated()
            );

        // Add filters
        http.addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(jwtAuthenticationFilter, ApiKeyAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public SecurityFilterChain webFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/**")
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/auth/**", "/api/containers/**", "/api/source/**", "/api/github/**", "/api/deployments/**", "/api/payments/**", "/webhooks/**", "/auth/github/**"))
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/", "/login", "/register", "/static/**", "/css/**", "/js/**").permitAll()
                .requestMatchers("/forgot-password", "/verify-otp", "/reset-password").permitAll()
                .requestMatchers("/auth/github/callback").permitAll()  // OAuth callback
                .requestMatchers("/webhooks/github").permitAll()  // GitHub webhooks
                .requestMatchers("/health", "/health/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers("/favicon.ico").permitAll()
                .requestMatchers("/about", "/contact", "/terms", "/privacy", "/refund").permitAll()
                // Documentation and public info pages
                .requestMatchers("/docs", "/api", "/status").permitAll()
                // SEO and marketing pages - must be public for search engines and AI tools
                .requestMatchers("/heroku-alternative", "/railway-alternative", "/render-alternative").permitAll()
                .requestMatchers("/docker-hosting", "/container-hosting").permitAll()
                .requestMatchers("/for/**", "/pricing").permitAll()
                .requestMatchers("/robots.txt", "/sitemap.xml").permitAll()
                .requestMatchers("/api/source/**").authenticated()
                .requestMatchers("/api/github/**").authenticated()  // GitHub API uses session auth
                .requestMatchers("/api/deployments/**").authenticated()  // Deployment logs uses session auth
                .requestMatchers("/api/payments/**").authenticated()  // Payment API uses session auth for browser
                .requestMatchers("/web/api/**").authenticated()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/dashboard", true)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            )
            .requiresChannel(channel -> channel
                .requestMatchers(r ->
                    r.getHeader("X-Forwarded-Proto") != null &&
                    !"https".equalsIgnoreCase(r.getHeader("X-Forwarded-Proto")))
                .requiresSecure()
            );

        http.authenticationProvider(authenticationProvider());

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(
            "http://localhost:3000",
            "https://containers.somdip.dev",
            "https://snapdeploy.dev",
            "https://www.snapdeploy.dev"
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
