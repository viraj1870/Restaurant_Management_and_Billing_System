package com.restaurant.rms.dto;

import com.restaurant.rms.model.DiscountType;
import com.restaurant.rms.model.LoyaltyType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class OfferDtos {

    private OfferDtos() {}

    public record CouponRequest(
            @NotBlank(message = "Coupon code is required")
            @Pattern(regexp = "^[A-Za-z0-9_-]{3,30}$", message = "Code must be 3-30 letters, digits, - or _") String code,
            @Size(max = 255) String description,
            @NotNull(message = "Discount type is required") DiscountType discountType,
            @NotNull(message = "Discount value is required") @DecimalMin(value = "0.01", message = "Discount must be positive") BigDecimal discountValue,
            @DecimalMin(value = "0.0") BigDecimal minOrderAmount,
            @DecimalMin(value = "0.0") BigDecimal maxDiscount,
            @NotNull(message = "Valid from date is required") LocalDate validFrom,
            @NotNull(message = "Valid to date is required") LocalDate validTo,
            @Min(1) Integer usageLimit,
            @Min(1) Integer perCustomerLimit,
            Boolean active) {}

    public record CouponDto(Long id, String code, String description, DiscountType discountType,
                            BigDecimal discountValue, BigDecimal minOrderAmount, BigDecimal maxDiscount,
                            LocalDate validFrom, LocalDate validTo, Integer usageLimit, Integer perCustomerLimit,
                            int usedCount, boolean active, boolean currentlyValid) {}

    public record LoyaltyTransactionDto(Long id, LoyaltyType type, int points, int balanceAfter, String description,
                                        String orderNumber, LocalDateTime createdAt) {}

    public record LoyaltySummary(int balance, int earnRateAmount, int maxRedeemPercent,
                                 java.util.List<LoyaltyTransactionDto> transactions) {}
}
