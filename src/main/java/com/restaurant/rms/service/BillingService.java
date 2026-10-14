package com.restaurant.rms.service;

import com.restaurant.rms.dto.OrderDtos.BillLine;
import com.restaurant.rms.dto.OrderDtos.BillPreview;
import com.restaurant.rms.dto.OrderDtos.CartItem;
import com.restaurant.rms.exception.ApiException;
import com.restaurant.rms.model.Coupon;
import com.restaurant.rms.model.MenuItem;
import com.restaurant.rms.model.Restaurant;
import com.restaurant.rms.model.User;
import com.restaurant.rms.repository.MenuItemRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Automatic bill calculation.
 * <pre>
 *   subtotal        = sum(unit price x quantity)      (prices always taken from the database)
 *   - coupon        = coupon discount on subtotal
 *   - loyalty       = redeemed points (capped by balance and max-redeem %)
 *   taxable amount  = subtotal - coupon - loyalty
 *   tax             = taxable amount x restaurant tax %
 *   total           = taxable amount + tax
 * </pre>
 */
@Service
public class BillingService {

    private final MenuItemRepository menuItemRepository;
    private final CouponService couponService;
    private final LoyaltyService loyaltyService;

    public BillingService(MenuItemRepository menuItemRepository, CouponService couponService,
                          LoyaltyService loyaltyService) {
        this.menuItemRepository = menuItemRepository;
        this.couponService = couponService;
        this.loyaltyService = loyaltyService;
    }

    public record CalculatedBill(BillPreview preview, Map<MenuItem, Integer> items, Coupon coupon) {}

    /**
     * @param strictCoupon when true an invalid coupon fails the request (order placement);
     *                     when false the preview just reports why the coupon was not applied.
     */
    public CalculatedBill calculate(Restaurant restaurant, List<CartItem> cart, String couponCode,
                                    Integer redeemPoints, User customer, boolean strictCoupon) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (CartItem ci : cart) {
            quantities.merge(ci.menuItemId(), ci.quantity(), Integer::sum);
        }

        Map<MenuItem, Integer> items = new LinkedHashMap<>();
        List<BillLine> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (var e : quantities.entrySet()) {
            MenuItem m = menuItemRepository.findByIdAndRestaurantIdAndDeletedFalse(e.getKey(), restaurant.getId())
                    .orElseThrow(() -> ApiException.badRequest("Menu item " + e.getKey() + " does not belong to this restaurant"));
            if (!m.isAvailable() || !m.getCategory().isActive()) {
                throw ApiException.badRequest("'" + m.getName() + "' is currently unavailable");
            }
            int qty = e.getValue();
            if (qty > 50) throw ApiException.badRequest("Quantity for '" + m.getName() + "' cannot exceed 50");
            BigDecimal line = m.getPrice().multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
            subtotal = subtotal.add(line);
            items.put(m, qty);
            lines.add(new BillLine(m.getId(), m.getName(), m.getPrice(), qty, line));
        }

        Coupon coupon = null;
        BigDecimal couponDiscount = BigDecimal.ZERO;
        String couponMessage = null;
        String appliedCode = null;
        if (couponCode != null && !couponCode.isBlank()) {
            try {
                coupon = couponService.validate(restaurant.getId(), couponCode, customer, subtotal);
                couponDiscount = couponService.discountFor(coupon, subtotal);
                appliedCode = coupon.getCode();
                couponMessage = "Coupon applied: you save \u20B9" + couponDiscount;
            } catch (ApiException ex) {
                if (strictCoupon) throw ex;
                couponMessage = ex.getMessage();
            }
        }

        BigDecimal afterCoupon = subtotal.subtract(couponDiscount);
        int balance = customer == null ? 0 : customer.getLoyaltyPoints();
        int maxRedeemable = loyaltyService.maxRedeemable(afterCoupon, balance);
        int requested = redeemPoints == null ? 0 : redeemPoints;
        if (requested > maxRedeemable) {
            if (strictCoupon) {
                throw ApiException.badRequest("You can redeem at most " + maxRedeemable + " points on this order");
            }
            requested = maxRedeemable;
        }
        BigDecimal loyaltyDiscount = loyaltyService.pointValue(requested).min(afterCoupon);

        BigDecimal taxable = afterCoupon.subtract(loyaltyDiscount).max(BigDecimal.ZERO);
        BigDecimal taxPercent = restaurant.getTaxPercent();
        BigDecimal tax = taxable.multiply(taxPercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal total = taxable.add(tax).setScale(2, RoundingMode.HALF_UP);

        BillPreview preview = new BillPreview(lines, subtotal.setScale(2, RoundingMode.HALF_UP), appliedCode,
                couponDiscount, couponMessage, requested, loyaltyDiscount, balance, maxRedeemable,
                taxPercent, tax, total, loyaltyService.pointsFor(total));
        return new CalculatedBill(preview, items, coupon);
    }
}
