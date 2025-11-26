package dev.somdip.containerplatform.service;


import dev.somdip.containerplatform.dto.auth.ApiKeyResponse;
import dev.somdip.containerplatform.dto.auth.JwtResponse;
import dev.somdip.containerplatform.dto.auth.LoginRequest;
import dev.somdip.containerplatform.dto.auth.RegisterRequest;
import dev.somdip.containerplatform.model.PasswordResetToken;
import dev.somdip.containerplatform.model.User;
import dev.somdip.containerplatform.repository.PasswordResetTokenRepository;
import dev.somdip.containerplatform.repository.UserRepository;
import dev.somdip.containerplatform.utils.JwtUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final EmailService emailService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private static final int OTP_EXPIRY_MINUTES = 10;
    private static final int MAX_OTP_ATTEMPTS = 5;

    public UserService(UserRepository userRepository,
                      PasswordEncoder passwordEncoder,
                      JwtUtils jwtUtils,
                      EmailService emailService,
                      PasswordResetTokenRepository passwordResetTokenRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.emailService = emailService;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
    }

    public JwtResponse register(RegisterRequest request) {
        log.debug("Registering new user: {}", request.getEmail());

        // Check if user already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        // Create new user
        User user = new User();
        user.setUserId(UUID.randomUUID().toString());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setUsername(request.getUsername() != null ? request.getUsername() : request.getEmail().split("@")[0]);
        user.setPlan(User.UserPlan.FREE);
        user.setStatus(User.UserStatus.ACTIVE);
        user.setEmailVerified(false);
        user.setContainerCount(0);
        user.setTotalDeployments(0L);
        
        // Set roles
        Set<String> roles = new HashSet<>();
        roles.add("ROLE_USER");
        user.setRoles(roles);

        // Generate API key
        String apiKey = generateApiKey();
        user.setApiKey(apiKey);

        // Generate email verification token
        user.setEmailVerificationToken(UUID.randomUUID().toString());

        // Save user
        user = userRepository.save(user);

        // Generate JWT token
        String jwt = jwtUtils.generateJwtToken(user.getUserId(), user.getEmail());

        log.info("User registered successfully: {}", user.getUserId());
        return new JwtResponse(jwt, user.getUserId(), user.getEmail(), apiKey);
    }

    public JwtResponse login(LoginRequest request) {
        log.debug("User login attempt: {}", request.getEmail());

        // Find user by email
        Optional<User> userOptional = userRepository.findByEmail(request.getEmail());
        if (userOptional.isEmpty()) {
            throw new RuntimeException("Invalid credentials");
        }

        User user = userOptional.get();

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid credentials");
        }

        // Update last login
        userRepository.updateLastLogin(user.getUserId());

        // Generate JWT token
        String jwt = jwtUtils.generateJwtToken(user.getUserId(), user.getEmail());

        log.info("User logged in successfully: {}", user.getUserId());
        return new JwtResponse(jwt, user.getUserId(), user.getEmail(), user.getApiKey());
    }

    public ApiKeyResponse regenerateApiKey(String userId) {
        log.debug("Regenerating API key for user: {}", userId);

        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            throw new RuntimeException("User not found");
        }

        User user = userOptional.get();
        String newApiKey = generateApiKey();
        user.setApiKey(newApiKey);
        userRepository.save(user);

        log.info("API key regenerated for user: {}", userId);
        return new ApiKeyResponse(newApiKey, "API key regenerated successfully");
    }

    public Optional<User> findByApiKey(String apiKey) {
        return userRepository.findByApiKey(apiKey);
    }

    public Optional<User> findById(String userId) {
        return userRepository.findById(userId);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public void verifyEmail(String token) {
        log.debug("Verifying email with token");
        
        // In a real implementation, you would find user by emailVerificationToken
        // For now, this is a placeholder
        throw new UnsupportedOperationException("Email verification not implemented yet");
    }

    private String generateApiKey() {
        // Generate a secure API key
        return "sk_" + UUID.randomUUID().toString().replace("-", "") +
               UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    // Password Reset Methods

    public void initiatePasswordReset(String email) {
        log.debug("Initiating password reset for email: {}", email);

        // Check if user exists
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isEmpty()) {
            // For security, don't reveal if email exists or not
            log.warn("Password reset requested for non-existent email: {}", email);
            return; // Return silently
        }

        User user = userOptional.get();

        // Generate 6-digit OTP
        String otp = generateOTP();

        // Create password reset token
        PasswordResetToken token = new PasswordResetToken();
        token.setEmail(email);
        token.setOtp(otp);
        token.setCreatedAt(Instant.now());
        token.setExpiryTime(Instant.now().plus(OTP_EXPIRY_MINUTES, ChronoUnit.MINUTES));
        token.setAttempts(0);
        token.setUsed(false);

        // Save token
        passwordResetTokenRepository.save(token);

        // Send OTP email
        try {
            emailService.sendPasswordResetOTP(email, otp, user.getEmail());
            log.info("Password reset OTP sent to: {}", email);
        } catch (Exception e) {
            log.error("Failed to send password reset email to: {}", email, e);
            throw new RuntimeException("Failed to send reset email. Please try again later.");
        }
    }

    public boolean verifyOTP(String email, String otp) {
        log.debug("Verifying OTP for email: {}", email);

        Optional<PasswordResetToken> tokenOptional = passwordResetTokenRepository.findByEmail(email);
        if (tokenOptional.isEmpty()) {
            log.warn("No password reset token found for email: {}", email);
            return false;
        }

        PasswordResetToken token = tokenOptional.get();

        // Check if already used
        if (token.isUsed()) {
            log.warn("OTP already used for email: {}", email);
            return false;
        }

        // Check if expired
        if (token.isExpired()) {
            log.warn("OTP expired for email: {}", email);
            passwordResetTokenRepository.delete(email);
            return false;
        }

        // Check attempts
        if (token.getAttempts() >= MAX_OTP_ATTEMPTS) {
            log.warn("Max OTP attempts exceeded for email: {}", email);
            passwordResetTokenRepository.delete(email);
            return false;
        }

        // Verify OTP
        if (!token.getOtp().equals(otp)) {
            // Increment attempts
            token.setAttempts(token.getAttempts() + 1);
            passwordResetTokenRepository.save(token);
            log.warn("Invalid OTP for email: {} | Attempts: {}", email, token.getAttempts());
            return false;
        }

        log.info("OTP verified successfully for email: {}", email);
        return true;
    }

    public void resetPassword(String email, String otp, String newPassword) {
        log.debug("Resetting password for email: {}", email);

        // Verify OTP again
        if (!verifyOTP(email, otp)) {
            throw new RuntimeException("Invalid or expired OTP");
        }

        // Find user
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isEmpty()) {
            throw new RuntimeException("User not found");
        }

        User user = userOptional.get();

        // Update password
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        // Mark OTP as used and delete token
        passwordResetTokenRepository.delete(email);

        log.info("Password reset successfully for user: {}", user.getUserId());
    }

    private String generateOTP() {
        SecureRandom random = new SecureRandom();
        int otp = 100000 + random.nextInt(900000); // 6-digit OTP
        return String.valueOf(otp);
    }
}