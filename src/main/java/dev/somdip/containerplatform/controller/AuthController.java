package dev.somdip.containerplatform.controller;

import dev.somdip.containerplatform.dto.auth.*;
import dev.somdip.containerplatform.security.CustomUserDetails;
import dev.somdip.containerplatform.service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*", maxAge = 3600)
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Helper method to extract userId from Authentication
     */
    private String getUserId(Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return userDetails.getUserId();
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest request) {
        try {
            JwtResponse response = userService.register(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("Registration failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: " + e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest request) {
        try {
            JwtResponse response = userService.login(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("Login failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: " + e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logoutUser() {
        // In a stateless JWT system, logout is handled client-side
        // by removing the token. Here we just return success.
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(new MessageResponse("Logged out successfully"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken() {
        // Get current authentication
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
        	return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        	        .body(new MessageResponse("Not authenticated"));
        }

        // In a real implementation, you would validate the refresh token
        // and issue a new access token
        return ResponseEntity.ok(new MessageResponse("Token refresh not implemented yet"));
    }

    @PostMapping("/api-key/regenerate")
    public ResponseEntity<?> regenerateApiKey(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
        	return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        	        .body(new MessageResponse("Not authenticated"));
        }

        String userId = getUserId(authentication);
        try {
            ApiKeyResponse response = userService.regenerateApiKey(userId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("API key regeneration failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: " + e.getMessage()));
        }
    }

    @GetMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestParam String token) {
        try {
            userService.verifyEmail(token);
            return ResponseEntity.ok(new MessageResponse("Email verified successfully"));
        } catch (Exception e) {
            log.error("Email verification failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: " + e.getMessage()));
        }
    }

    // Password Reset Endpoints

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        try {
            userService.initiatePasswordReset(request.getEmail());
            return ResponseEntity.ok(new MessageResponse(
                "If an account with that email exists, we've sent a password reset code."));
        } catch (Exception e) {
            log.error("Forgot password failed: {}", e.getMessage());
            // Don't reveal if email exists or not for security
            return ResponseEntity.ok(new MessageResponse(
                "If an account with that email exists, we've sent a password reset code."));
        }
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOTP(@Valid @RequestBody VerifyOtpRequest request) {
        try {
            boolean isValid = userService.verifyOTP(request.getEmail(), request.getOtp());
            if (isValid) {
                return ResponseEntity.ok(new MessageResponse("OTP verified successfully"));
            } else {
                return ResponseEntity.badRequest()
                        .body(new MessageResponse("Invalid or expired OTP"));
            }
        } catch (Exception e) {
            log.error("OTP verification failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: " + e.getMessage()));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        try {
            userService.resetPassword(request.getEmail(), request.getOtp(), request.getNewPassword());
            return ResponseEntity.ok(new MessageResponse("Password reset successfully"));
        } catch (Exception e) {
            log.error("Password reset failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: " + e.getMessage()));
        }
    }
}