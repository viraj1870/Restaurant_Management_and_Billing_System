package com.restaurant.rms.service;

import com.restaurant.rms.config.AppProperties;
import com.restaurant.rms.dto.OfferDtos.LoyaltySummary;
import com.restaurant.rms.exception.ApiException;
import com.restaurant.rms.model.LoyaltyTransaction;
import com.restaurant.rms.model.LoyaltyType;
import com.restaurant.rms.model.Order;
import com.restaurant.rms.model.User;
import com.restaurant.rms.repository.LoyaltyTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class LoyaltyService {

    private final LoyaltyTransactionRepository repository;
    private final AppProperties props;
    private final DtoMapper mapper;

    public LoyaltyService(LoyaltyTransactionRepository repository, AppProperties props, DtoMapper mapper) {
        this.repository = repository;
        this.props = props;
        this.mapper = mapper;
    }

    /** Points earned for a final bill amount (1 point per configured rupee amount). */
    public int pointsFor(BigDecimal amount) {
        int per = Math.max(1, props.getLoyalty().getEarnPerAmount());
        return amount.divide(BigDecimal.valueOf(per), 0, RoundingMode.FLOOR).intValue();
    }

    public BigDecimal pointValue(int points) {
        return BigDecimal.valueOf((long) points * props.getLoyalty().getPointValue()).setScale(2, RoundingMode.HALF_UP);
    }

    /** Max points that can be applied to the given (post-coupon) amount. */
    public int maxRedeemable(BigDecimal amount, int balance) {
        BigDecimal cap = amount.multiply(BigDecimal.valueOf(props.getLoyalty().getMaxRedeemPercent()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.FLOOR);
        int byValue = cap.divide(BigDecimal.valueOf(Math.max(1, props.getLoyalty().getPointValue())), 0, RoundingMode.FLOOR).intValue();
        return Math.max(0, Math.min(balance, byValue));
    }

    @Transactional
    public void credit(User customer, Order order, int points, LoyaltyType type, String description) {
        if (points <= 0) return;
        customer.setLoyaltyPoints(customer.getLoyaltyPoints() + points);
        log(customer, order, type, points, description);
    }

    @Transactional
    public void debit(User customer, Order order, int points, LoyaltyType type, String description) {
        if (points <= 0) return;
        if (customer.getLoyaltyPoints() < points) {
            throw ApiException.badRequest("Insufficient loyalty points");
        }
        customer.setLoyaltyPoints(customer.getLoyaltyPoints() - points);
        log(customer, order, type, -points, description);
    }

    @Transactional(readOnly = true)
    public LoyaltySummary summary(User customer) {
        return new LoyaltySummary(customer.getLoyaltyPoints(), props.getLoyalty().getEarnPerAmount(),
                props.getLoyalty().getMaxRedeemPercent(),
                repository.findByCustomerIdOrderByCreatedAtDesc(customer.getId()).stream().map(mapper::loyalty).toList());
    }

    private void log(User customer, Order order, LoyaltyType type, int points, String description) {
        LoyaltyTransaction t = new LoyaltyTransaction();
        t.setCustomer(customer);
        t.setOrder(order);
        t.setType(type);
        t.setPoints(points);
        t.setBalanceAfter(customer.getLoyaltyPoints());
        t.setDescription(description);
        repository.save(t);
    }
}
