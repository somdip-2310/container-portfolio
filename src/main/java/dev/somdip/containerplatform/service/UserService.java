package dev.somdip.containerplatform.service;


import dev.somdip.containerplatform.dto.auth.ApiKeyResponse;
import dev.somdip.containerplatform.dto.auth.JwtResponse;
import dev.somdip.containerplatform.dto.auth.LoginRequest;
import dev.somdip.containerplatform.dto.auth.RegisterRequest;
import dev.somdip.containerplatform.model.User;
import dev.somdip.containerplatform.repository.UserRepository;
import dev.somdip.containerplatform.utils.JwtUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
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

    public UserService(UserRepository userRepository, 
                      PasswordEncoder passwordEncoder, 
                      JwtUtils jwtUtils) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
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
        user.setStatus(User.UserStatus.PENDING_VERIFICATION);
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
}