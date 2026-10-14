package com.restaurant.rms.dto;

import com.restaurant.rms.model.Role;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public final class AuthDtos {

    private AuthDtos() {}

    public static final String MOBILE_REGEX = "^[6-9]\\d{9}$";
    public static final String PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$";
    public static final String PASSWORD_MSG = "Password must be 8-64 characters with at least one letter and one digit";

    public record CustomerSignupRequest(
            @NotBlank(message = "Name is required") @Size(max = 100) String name,
            @NotBlank(message = "Email is required") @Email(message = "Invalid email") @Size(max = 150) String email,
            @NotBlank(message = "Mobile is required") @Pattern(regexp = MOBILE_REGEX, message = "Mobile must be a valid 10-digit Indian number") String mobile,
            @NotBlank(message = "Password is required") @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MSG) String password,
            @Size(max = 255) String address,
            Boolean notificationConsent) {}

    public record MerchantSignupRequest(
            @NotBlank(message = "Name is required") @Size(max = 100) String name,
            @NotBlank(message = "Email is required") @Email(message = "Invalid email") @Size(max = 150) String email,
            @NotBlank(message = "Mobile is required") @Pattern(regexp = MOBILE_REGEX, message = "Mobile must be a valid 10-digit Indian number") String mobile,
            @NotBlank(message = "Password is required") @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MSG) String password,
            @NotBlank(message = "Restaurant name is required") @Size(max = 150) String restaurantName,
            @Size(max = 255) String restaurantAddress,
            @Size(max = 100) String cuisine,
            @DecimalMin("0.0") @DecimalMax("28.0") BigDecimal taxPercent) {}

    public record SigninRequest(
            @NotBlank(message = "Email or mobile is required") String login,
            @NotBlank(message = "Password is required") String password) {}

    public record AuthResponse(String token, long expiresInMs, UserDto user) {}

    public record UserDto(Long id, String name, String email, String mobile, Role role, String address,
                          int loyaltyPoints, boolean notificationConsent, Long restaurantId, String restaurantName) {}

    public record UpdateProfileRequest(
            @NotBlank(message = "Name is required") @Size(max = 100) String name,
            @NotBlank(message = "Mobile is required") @Pattern(regexp = MOBILE_REGEX, message = "Mobile must be a valid 10-digit Indian number") String mobile,
            @Size(max = 255) String address,
            Boolean notificationConsent) {}

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MSG) String newPassword) {}

    public record StaffRequest(
            @NotBlank(message = "Name is required") @Size(max = 100) String name,
            @NotBlank @Email(message = "Invalid email") String email,
            @NotBlank @Pattern(regexp = MOBILE_REGEX, message = "Mobile must be a valid 10-digit Indian number") String mobile,
            @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MSG) String password) {}

    public record StaffDto(Long id, String name, String email, String mobile, boolean enabled) {}
}
