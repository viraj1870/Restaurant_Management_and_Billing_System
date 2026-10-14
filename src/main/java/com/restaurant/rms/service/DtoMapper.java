package com.restaurant.rms.service;

import com.restaurant.rms.dto.AuthDtos.StaffDto;
import com.restaurant.rms.dto.AuthDtos.UserDto;
import com.restaurant.rms.dto.FeedbackDtos.FeedbackDto;
import com.restaurant.rms.dto.FeedbackDtos.NotificationDto;
import com.restaurant.rms.dto.MenuDtos.CategoryDto;
import com.restaurant.rms.dto.MenuDtos.MenuItemDto;
import com.restaurant.rms.dto.MenuDtos.RestaurantDto;
import com.restaurant.rms.dto.OfferDtos.CouponDto;
import com.restaurant.rms.dto.OfferDtos.LoyaltyTransactionDto;
import com.restaurant.rms.dto.OrderDtos.OrderDto;
import com.restaurant.rms.dto.OrderDtos.OrderItemDto;
import com.restaurant.rms.dto.OrderDtos.PaymentDto;
import com.restaurant.rms.model.*;
import com.restaurant.rms.repository.FeedbackRepository;
import com.restaurant.rms.repository.RestaurantRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class DtoMapper {

    private final RestaurantRepository restaurantRepository;
    private final FeedbackRepository feedbackRepository;

    public DtoMapper(RestaurantRepository restaurantRepository, FeedbackRepository feedbackRepository) {
        this.restaurantRepository = restaurantRepository;
        this.feedbackRepository = feedbackRepository;
    }

    public UserDto user(User u) {
        Restaurant r = switch (u.getRole()) {
            case MERCHANT -> restaurantRepository.findByOwnerId(u.getId()).orElse(null);
            case KITCHEN -> u.getRestaurant();
            default -> null;
        };
        return new UserDto(u.getId(), u.getName(), u.getEmail(), u.getMobile(), u.getRole(), u.getAddress(),
                u.getLoyaltyPoints(), u.isNotificationConsent(),
                r == null ? null : r.getId(), r == null ? null : r.getName());
    }

    public StaffDto staff(User u) {
        return new StaffDto(u.getId(), u.getName(), u.getEmail(), u.getMobile(), u.isEnabled());
    }

    public RestaurantDto restaurant(Restaurant r, Double avgRating, long ratingCount) {
        return new RestaurantDto(r.getId(), r.getName(), r.getDescription(), r.getCuisine(), r.getAddress(),
                r.getPhone(), r.getEmail(), r.getGstNumber(), r.getTaxPercent(), r.isActive(),
                r.isAcceptingOrders(), avgRating, ratingCount);
    }

    public CategoryDto category(Category c, long itemCount) {
        return new CategoryDto(c.getId(), c.getName(), c.getDescription(), c.getDisplayOrder(), c.isActive(), itemCount);
    }

    public MenuItemDto menuItem(MenuItem m) {
        return new MenuItemDto(m.getId(), m.getCategory().getId(), m.getCategory().getName(), m.getName(),
                m.getDescription(), m.getPrice(), m.isVegetarian(), m.isAvailable(), m.getImageUrl());
    }

    public OrderDto order(Order o) {
        List<OrderItemDto> items = o.getItems().stream()
                .map(i -> new OrderItemDto(i.getMenuItem().getId(), i.getItemName(), i.getUnitPrice(),
                        i.getQuantity(), i.getLineTotal()))
                .toList();
        boolean feedbackGiven = o.getId() != null && feedbackRepository.existsByOrderIdAndType(o.getId(), FeedbackType.REVIEW);
        return new OrderDto(o.getId(), o.getOrderNumber(), o.getRestaurant().getId(), o.getRestaurant().getName(),
                o.getCustomer().getId(), o.getCustomer().getName(), o.getCustomer().getMobile(),
                items, o.getOrderType(), o.getTableNumber(), o.getSpecialInstructions(),
                o.getSubtotal(), o.getCouponCode(), o.getCouponDiscount(),
                o.getLoyaltyPointsRedeemed(), o.getLoyaltyDiscount(),
                o.getTaxPercent(), o.getTaxAmount(), o.getTotalAmount(), o.getLoyaltyPointsEarned(),
                o.getStatus(), o.getPaymentStatus(), new ArrayList<>(o.getStatus().nextAllowed()), feedbackGiven,
                o.getCreatedAt(), o.getUpdatedAt(), o.getCompletedAt());
    }

    public PaymentDto payment(Payment p) {
        return new PaymentDto(p.getId(), p.getOrder().getId(), p.getOrder().getOrderNumber(), p.getMethod(),
                p.getAmount(), p.getStatus(), p.getTransactionRef(), p.getGateway(), p.getFailureReason(),
                p.getRecordedBy() == null ? null : p.getRecordedBy().getName(), p.getCreatedAt());
    }

    public CouponDto coupon(Coupon c) {
        LocalDate today = LocalDate.now();
        boolean valid = c.isActive() && !today.isBefore(c.getValidFrom()) && !today.isAfter(c.getValidTo())
                && (c.getUsageLimit() == null || c.getUsedCount() < c.getUsageLimit());
        return new CouponDto(c.getId(), c.getCode(), c.getDescription(), c.getDiscountType(), c.getDiscountValue(),
                c.getMinOrderAmount(), c.getMaxDiscount(), c.getValidFrom(), c.getValidTo(), c.getUsageLimit(),
                c.getPerCustomerLimit(), c.getUsedCount(), c.isActive(), valid);
    }

    public LoyaltyTransactionDto loyalty(LoyaltyTransaction t) {
        return new LoyaltyTransactionDto(t.getId(), t.getType(), t.getPoints(), t.getBalanceAfter(),
                t.getDescription(), t.getOrder() == null ? null : t.getOrder().getOrderNumber(), t.getCreatedAt());
    }

    public FeedbackDto feedback(Feedback f) {
        return new FeedbackDto(f.getId(), f.getRestaurant().getId(), f.getRestaurant().getName(),
                f.getCustomer().getId(), f.getCustomer().getName(),
                f.getOrder() == null ? null : f.getOrder().getId(),
                f.getOrder() == null ? null : f.getOrder().getOrderNumber(),
                f.getType(), f.getRating(), f.getComment(), f.getStatus(), f.getMerchantReply(),
                f.getCreatedAt(), f.getResolvedAt());
    }

    public NotificationDto notification(NotificationLog n) {
        return new NotificationDto(n.getId(), n.getChannel(), n.getRecipient(), n.getSubject(), n.getMessage(),
                n.getStatus(), n.getOrder() == null ? null : n.getOrder().getOrderNumber(), n.getCreatedAt());
    }
}
