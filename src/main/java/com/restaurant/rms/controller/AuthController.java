package com.restaurant.rms.controller;

import com.restaurant.rms.dto.AuthDtos.*;
import com.restaurant.rms.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/api/auth/merchant/signup")
    public ResponseEntity<AuthResponse> merchantSignup(@Valid @RequestBody MerchantSignupRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signupMerchant(req));
    }

    @PostMapping("/api/auth/customer/signup")
    public ResponseEntity<AuthResponse> customerSignup(@Valid @RequestBody CustomerSignupRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signupCustomer(req));
    }

    @PostMapping("/api/auth/signin")
    public AuthResponse signin(@Valid @RequestBody SigninRequest req) {
        return authService.signin(req);
    }

    @GetMapping("/api/users/me")
    public UserDto me() {
        return authService.me();
    }

    @PutMapping("/api/users/me")
    public UserDto updateProfile(@Valid @RequestBody UpdateProfileRequest req) {
        return authService.updateProfile(req);
    }

    @PutMapping("/api/users/me/password")
    public Map<String, String> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        authService.changePassword(req);
        return Map.of("message", "Password updated successfully");
    }
}
