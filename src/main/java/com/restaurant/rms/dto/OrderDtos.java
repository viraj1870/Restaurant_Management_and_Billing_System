package com.restaurant.rms.dto;

import com.restaurant.rms.model.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class OrderDtos {

    private OrderDtos() {}

    public record CartItem(
            @NotNull(message = "Menu item is required") Long menuItemId,
            @NotNull @Min(value = 1, message = "Quantity must be at least 1")
            @Max(value = 50, message = "Quantity cannot exceed 50") Integer quantity) {}

    public record OrderRequest(
            @NotNull(message = "Restaurant is required") Long restaurantId,
            @NotEmpty(message = "Cart is empty") @Size(max = 50) List<@Valid CartItem> items,
            @Size(max = 30) String couponCode,
            @Min(value = 0, message = "Points cannot be negative") Integer redeemPoints,
            OrderType orderType,
            @Size(max = 10) String tableNumber,
            @Size(max = 500) String specialInstructions) {}

    public record BillLine(Long menuItemId, String name, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {}

    public record BillPreview(List<BillLine> lines, BigDecimal subtotal, String couponCode, BigDecimal couponDiscount,
                              String couponMessage, int loyaltyPointsRedeemed, BigDecimal loyaltyDiscount,
                              int availablePoints, int maxRedeemablePoints, BigDecimal taxPercent,
                              BigDecimal taxAmount, BigDecimal totalAmount, int pointsToEarn) {}

    public record OrderItemDto(Long menuItemId, String itemName, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {}

    public record OrderDto(Long id, String orderNumber, Long restaurantId, String restaurantName,
                           Long customerId, String customerName, String customerMobile,
                           List<OrderItemDto> items, OrderType orderType, String tableNumber, String specialInstructions,
                           BigDecimal subtotal, String couponCode, BigDecimal couponDiscount,
                           int loyaltyPointsRedeemed, BigDecimal loyaltyDiscount,
                           BigDecimal taxPercent, BigDecimal taxAmount, BigDecimal totalAmount,
                           int loyaltyPointsEarned, OrderStatus status, PaymentStatus paymentStatus,
                           List<OrderStatus> nextStatuses, boolean feedbackGiven,
                           LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime completedAt) {}

    public record StatusUpdateRequest(@NotNull(message = "Status is required") OrderStatus status) {}

    public record PaymentRequest(
            @NotNull(message = "Payment method is required") PaymentMethod method,
            @Size(max = 19) String cardNumber,
            @Size(max = 60) String upiId,
            @Size(max = 60) String bankCode) {}

    public record PaymentDto(Long id, Long orderId, String orderNumber, PaymentMethod method, BigDecimal amount,
                             TransactionStatus status, String transactionRef, String gateway,
                             String failureReason, String recordedBy, LocalDateTime createdAt) {}

    public record ReceiptDto(MenuDtos.RestaurantDto restaurant, OrderDto order, List<PaymentDto> payments,
                             LocalDateTime generatedAt) {}
}
