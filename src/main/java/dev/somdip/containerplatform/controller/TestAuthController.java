package dev.somdip.containerplatform.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
public class TestAuthController {

    @GetMapping("/public")
    public ResponseEntity<?> publicAccess() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Public content - no authentication required");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> userAccess(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "User content - authentication required");
        response.put("userId", authentication.getName());
        response.put("authorities", authentication.getAuthorities());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> adminAccess(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Admin content - admin role required");
        response.put("userId", authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "Not authenticated");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("userId", authentication.getName());
        response.put("authorities", authentication.getAuthorities());
        response.put("authenticated", authentication.isAuthenticated());
        return ResponseEntity.ok(response);
    }
}