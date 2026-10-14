package com.restaurant.rms.service;

import com.restaurant.rms.dto.OfferDtos.CouponDto;
import com.restaurant.rms.dto.OfferDtos.CouponRequest;
import com.restaurant.rms.exception.ApiException;
import com.restaurant.rms.model.*;
import com.restaurant.rms.repository.CouponRepository;
import com.restaurant.rms.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class CouponService {

    private final CouponRepository couponRepository;
    private final OrderRepository orderRepository;
    private final CurrentUserService currentUser;
    private final DtoMapper mapper;

    public CouponService(CouponRepository couponRepository, OrderRepository orderRepository,
                         CurrentUserService currentUser, DtoMapper mapper) {
        this.couponRepository = couponRepository;
        this.orderRepository = orderRepository;
        this.currentUser = currentUser;
        this.mapper = mapper;
    }

    /**
     * Validates a coupon for a customer and subtotal. Throws a 400 with a human readable
     * reason if the coupon is invalid, expired, exhausted or below the minimum order value.
     * Validation failures must not mark the caller's transaction rollback-only (preview catches them).
     */
    @Transactional(readOnly = true, noRollbackFor = ApiException.class)
    public Coupon validate(Long restaurantId, String code, User customer, BigDecimal subtotal) {
        Coupon c = couponRepository.findByRestaurantIdAndCodeIgnoreCase(restaurantId, code.trim())
                .orElseThrow(() -> ApiException.badRequest("Invalid coupon code"));
        LocalDate today = LocalDate.now();
        if (!c.isActive()) throw ApiException.badRequest("This coupon is no longer active");
        if (today.isBefore(c.getValidFrom())) throw ApiException.badRequest("This coupon is valid from " + c.getValidFrom());
        if (today.isAfter(c.getValidTo())) throw ApiException.badRequest("This coupon has expired");
        if (c.getUsageLimit() != null && c.getUsedCount() >= c.getUsageLimit()) {
            throw ApiException.badRequest("This coupon has reached its usage limit");
        }
        if (c.getPerCustomerLimit() != null && customer != null) {
            long used = orderRepository.countByCustomerIdAndCouponCodeIgnoreCaseAndRestaurantIdAndStatusNot(
                    customer.getId(), c.getCode(), restaurantId, OrderStatus.CANCELLED);
            if (used >= c.getPerCustomerLimit()) {
                throw ApiException.badRequest("You have already used this coupon the maximum number of times");
            }
        }
        if (subtotal.compareTo(c.getMinOrderAmount()) < 0) {
            throw ApiException.badRequest("Minimum order amount for this coupon is \u20B9" + c.getMinOrderAmount());
        }
        return c;
    }

    public BigDecimal discountFor(Coupon c, BigDecimal subtotal) {
        BigDecimal discount;
        if (c.getDiscountType() == DiscountType.PERCENT) {
            discount = subtotal.multiply(c.getDiscountValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (c.getMaxDiscount() != null && discount.compareTo(c.getMaxDiscount()) > 0) {
                discount = c.getMaxDiscount();
            }
        } else {
            discount = c.getDiscountValue();
        }
        return discount.min(subtotal).setScale(2, RoundingMode.HALF_UP);
    }

    /** Coupons currently usable by customers of a restaurant. */
    public List<CouponDto> activeForRestaurant(Long restaurantId) {
        return couponRepository.findByRestaurantIdOrderByIdDesc(restaurantId).stream()
                .map(mapper::coupon).filter(CouponDto::currentlyValid).toList();
    }

    // ---------- Merchant CRUD ----------

    public List<CouponDto> myCoupons() {
        Restaurant r = currentUser.merchantRestaurant();
        return couponRepository.findByRestaurantIdOrderByIdDesc(r.getId()).stream().map(mapper::coupon).toList();
    }

    @Transactional
    public CouponDto create(CouponRequest req) {
        Restaurant r = currentUser.merchantRestaurant();
        String code = req.code().trim().toUpperCase();
        if (couponRepository.existsByRestaurantIdAndCodeIgnoreCase(r.getId(), code)) {
            throw ApiException.conflict("Coupon code already exists");
        }
        Coupon c = new Coupon();
        c.setRestaurant(r);
        c.setCode(code);
        apply(c, req);
        return mapper.coupon(couponRepository.save(c));
    }

    @Transactional
    public CouponDto update(Long id, CouponRequest req) {
        Restaurant r = currentUser.merchantRestaurant();
        Coupon c = couponRepository.findByIdAndRestaurantId(id, r.getId())
                .orElseThrow(() -> ApiException.notFound("Coupon not found"));
        String code = req.code().trim().toUpperCase();
        if (!code.equalsIgnoreCase(c.getCode()) && couponRepository.existsByRestaurantIdAndCodeIgnoreCase(r.getId(), code)) {
            throw ApiException.conflict("Coupon code already exists");
        }
        c.setCode(code);
        apply(c, req);
        return mapper.coupon(c);
    }

    @Transactional
    public void delete(Long id) {
        Restaurant r = currentUser.merchantRestaurant();
        Coupon c = couponRepository.findByIdAndRestaurantId(id, r.getId())
                .orElseThrow(() -> ApiException.notFound("Coupon not found"));
        couponRepository.delete(c);
    }

    private void apply(Coupon c, CouponRequest req) {
        if (req.validTo().isBefore(req.validFrom())) {
            throw ApiException.badRequest("Valid-to date must be on or after valid-from date");
        }
        if (req.discountType() == DiscountType.PERCENT && req.discountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw ApiException.badRequest("Percentage discount cannot exceed 100");
        }
        c.setDescription(req.description());
        c.setDiscountType(req.discountType());
        c.setDiscountValue(req.discountValue());
        c.setMinOrderAmount(req.minOrderAmount() == null ? BigDecimal.ZERO : req.minOrderAmount());
        c.setMaxDiscount(req.maxDiscount());
        c.setValidFrom(req.validFrom());
        c.setValidTo(req.validTo());
        c.setUsageLimit(req.usageLimit());
        c.setPerCustomerLimit(req.perCustomerLimit());
        if (req.active() != null) c.setActive(req.active());
    }
}
