package com.restaurant.rms.dto;

import com.restaurant.rms.model.FeedbackStatus;
import com.restaurant.rms.model.FeedbackType;
import com.restaurant.rms.model.NotificationChannel;
import com.restaurant.rms.model.NotificationStatus;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

public final class FeedbackDtos {

    private FeedbackDtos() {}

    public record FeedbackRequest(
            Long orderId,
            Long restaurantId,
            FeedbackType type,
            @Min(value = 1, message = "Rating must be between 1 and 5") @Max(value = 5, message = "Rating must be between 1 and 5") Integer rating,
            @Size(max = 1000, message = "Comment is too long") String comment) {}

    public record FeedbackDto(Long id, Long restaurantId, String restaurantName, Long customerId, String customerName,
                              Long orderId, String orderNumber, FeedbackType type, Integer rating, String comment,
                              FeedbackStatus status, String merchantReply, LocalDateTime createdAt,
                              LocalDateTime resolvedAt) {}

    public record FeedbackUpdateRequest(FeedbackStatus status, @Size(max = 1000) String reply) {}

    public record NotificationDto(Long id, NotificationChannel channel, String recipient, String subject,
                                  String message, NotificationStatus status, String orderNumber,
                                  LocalDateTime createdAt) {}

    public record CustomerSummaryDto(Long id, String name, String email, String mobile, int loyaltyPoints,
                                     long totalOrders, java.math.BigDecimal totalSpent, LocalDateTime lastOrderAt) {}
}
