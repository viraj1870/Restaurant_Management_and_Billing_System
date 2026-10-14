package com.restaurant.rms.service;

import com.restaurant.rms.dto.OrderDtos.*;
import com.restaurant.rms.exception.ApiException;
import com.restaurant.rms.model.*;
import com.restaurant.rms.repository.OrderRepository;
import com.restaurant.rms.repository.PaymentRepository;
import com.restaurant.rms.service.notification.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class OrderService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter NUMBER_DATE = DateTimeFormatter.ofPattern("yyMMdd");

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final RestaurantService restaurantService;
    private final BillingService billingService;
    private final LoyaltyService loyaltyService;
    private final NotificationService notificationService;
    private final CurrentUserService currentUser;
    private final DtoMapper mapper;

    public OrderService(OrderRepository orderRepository, PaymentRepository paymentRepository,
                        RestaurantService restaurantService, BillingService billingService,
                        LoyaltyService loyaltyService, NotificationService notificationService,
                        CurrentUserService currentUser, DtoMapper mapper) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.restaurantService = restaurantService;
        this.billingService = billingService;
        this.loyaltyService = loyaltyService;
        this.notificationService = notificationService;
        this.currentUser = currentUser;
        this.mapper = mapper;
    }

    /** Bill preview for the cart; does not persist anything. */
    public BillPreview preview(OrderRequest req) {
        User customer = currentUser.requireRole(Role.CUSTOMER);
        Restaurant r = restaurantService.activeRestaurant(req.restaurantId());
        return billingService.calculate(r, req.items(), req.couponCode(), req.redeemPoints(), customer, false).preview();
    }

    @Transactional
    public OrderDto placeOrder(OrderRequest req) {
        User customer = currentUser.requireRole(Role.CUSTOMER);
        Restaurant r = restaurantService.activeRestaurant(req.restaurantId());
        if (!r.isAcceptingOrders()) {
            throw ApiException.badRequest(r.getName() + " is not accepting orders right now");
        }
        var bill = billingService.calculate(r, req.items(), req.couponCode(), req.redeemPoints(), customer, true);
        BillPreview p = bill.preview();

        Order o = new Order();
        o.setOrderNumber(generateOrderNumber());
        o.setCustomer(customer);
        o.setRestaurant(r);
        o.setOrderType(req.orderType() == null ? OrderType.DINE_IN : req.orderType());
        o.setTableNumber(o.getOrderType() == OrderType.DINE_IN ? blankToNull(req.tableNumber()) : null);
        o.setSpecialInstructions(blankToNull(req.specialInstructions()));
        bill.items().forEach((menuItem, qty) -> {
            OrderItem oi = new OrderItem();
            oi.setMenuItem(menuItem);
            oi.setItemName(menuItem.getName());
            oi.setUnitPrice(menuItem.getPrice());
            oi.setQuantity(qty);
            oi.setLineTotal(menuItem.getPrice().multiply(java.math.BigDecimal.valueOf(qty)));
            o.addItem(oi);
        });
        o.setSubtotal(p.subtotal());
        o.setCouponCode(p.couponCode());
        o.setCouponDiscount(p.couponDiscount());
        o.setLoyaltyPointsRedeemed(p.loyaltyPointsRedeemed());
        o.setLoyaltyDiscount(p.loyaltyDiscount());
        o.setTaxPercent(p.taxPercent());
        o.setTaxAmount(p.taxAmount());
        o.setTotalAmount(p.totalAmount());
        orderRepository.save(o);

        if (bill.coupon() != null) {
            bill.coupon().setUsedCount(bill.coupon().getUsedCount() + 1);
        }
        loyaltyService.debit(customer, o, p.loyaltyPointsRedeemed(), LoyaltyType.REDEEMED,
                "Redeemed on order " + o.getOrderNumber());

        notificationService.orderPlaced(o);
        return mapper.order(o);
    }

    public List<OrderDto> myOrders() {
        User customer = currentUser.requireRole(Role.CUSTOMER);
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId()).stream().map(mapper::order).toList();
    }

    public OrderDto get(Long id) {
        return mapper.order(accessibleOrder(id));
    }

    /** Orders of the merchant's restaurant, optionally filtered by status. */
    public List<OrderDto> restaurantOrders(OrderStatus status) {
        Restaurant r = currentUser.merchantRestaurant();
        List<Order> list = status == null
                ? orderRepository.findByRestaurantIdOrderByCreatedAtDesc(r.getId())
                : orderRepository.findByRestaurantIdAndStatusOrderByCreatedAtDesc(r.getId(), status);
        return list.stream().map(mapper::order).toList();
    }

    /** Live kitchen board: Pending, Preparing and Ready orders, oldest first. */
    public List<OrderDto> kitchenOrders() {
        Restaurant r = currentUser.staffRestaurant();
        return orderRepository.findByRestaurantIdAndStatusInOrderByCreatedAtAsc(r.getId(), OrderStatus.KITCHEN_ACTIVE)
                .stream().map(mapper::order).toList();
    }

    @Transactional
    public OrderDto updateStatus(Long id, OrderStatus target) {
        User actor = currentUser.user();
        Order o = accessibleOrder(id);
        if (actor.getRole() == Role.CUSTOMER) {
            throw ApiException.forbidden("Customers cannot change order status. Use cancel instead.");
        }
        if (actor.getRole() == Role.KITCHEN && target != OrderStatus.PREPARING && target != OrderStatus.READY) {
            throw ApiException.forbidden("Kitchen staff can only mark orders as Preparing or Ready");
        }
        if (!o.getStatus().canMoveTo(target)) {
            throw ApiException.badRequest("Cannot change order status from " + o.getStatus() + " to " + target);
        }
        if (target == OrderStatus.COMPLETED && o.getPaymentStatus() != PaymentStatus.PAID) {
            throw ApiException.badRequest("Order must be paid before it can be completed");
        }
        if (target == OrderStatus.CANCELLED) {
            cancelInternal(o, "Cancelled by restaurant");
        } else {
            o.setStatus(target);
            if (target == OrderStatus.COMPLETED) {
                o.setCompletedAt(LocalDateTime.now());
                awardPoints(o);
            }
        }
        notificationService.orderStatusChanged(o);
        return mapper.order(o);
    }

    /** Customer can cancel only while the kitchen has not started preparing. */
    @Transactional
    public OrderDto cancelByCustomer(Long id) {
        User customer = currentUser.requireRole(Role.CUSTOMER);
        Order o = accessibleOrder(id);
        if (!o.getCustomer().getId().equals(customer.getId())) throw ApiException.notFound("Order not found");
        if (o.getStatus() != OrderStatus.PENDING) {
            throw ApiException.badRequest("Order can only be cancelled before the kitchen starts preparing it");
        }
        cancelInternal(o, "Cancelled by customer");
        notificationService.orderStatusChanged(o);
        return mapper.order(o);
    }

    private void cancelInternal(Order o, String reason) {
        o.setStatus(OrderStatus.CANCELLED);
        loyaltyService.credit(o.getCustomer(), o, o.getLoyaltyPointsRedeemed(), LoyaltyType.REVERSED,
                "Points returned - order " + o.getOrderNumber() + " cancelled");
        if (o.getPaymentStatus() == PaymentStatus.PENDING) {
            o.setPaymentStatus(PaymentStatus.UNPAID);
            paymentRepository.findByOrderIdOrderByCreatedAtDesc(o.getId()).stream()
                    .filter(p -> p.getStatus() == TransactionStatus.PENDING)
                    .forEach(p -> {
                        p.setStatus(TransactionStatus.FAILED);
                        p.setFailureReason("Order cancelled");
                    });
        }
        if (o.getPaymentStatus() == PaymentStatus.PAID) {
            o.setPaymentStatus(PaymentStatus.REFUNDED);
            paymentRepository.findByOrderIdOrderByCreatedAtDesc(o.getId()).stream()
                    .filter(p -> p.getStatus() == TransactionStatus.SUCCESS)
                    .forEach(p -> p.setStatus(TransactionStatus.REFUNDED));
        }
    }

    void awardPoints(Order o) {
        if (o.getLoyaltyPointsEarned() > 0) return;
        int points = loyaltyService.pointsFor(o.getTotalAmount());
        if (points > 0) {
            o.setLoyaltyPointsEarned(points);
            loyaltyService.credit(o.getCustomer(), o, points, LoyaltyType.EARNED, "Earned on order " + o.getOrderNumber());
        }
    }

    /**
     * Ownership check: customers see only their orders, merchants and kitchen staff only orders
     * of their own restaurant. Unauthorised access returns 404 to avoid leaking order ids.
     */
    Order accessibleOrder(Long id) {
        User u = currentUser.user();
        Order o = orderRepository.findById(id).orElseThrow(() -> ApiException.notFound("Order not found"));
        boolean allowed = switch (u.getRole()) {
            case CUSTOMER -> o.getCustomer().getId().equals(u.getId());
            case MERCHANT, KITCHEN -> o.getRestaurant().getId().equals(currentUser.staffRestaurant().getId());
        };
        if (!allowed) throw ApiException.notFound("Order not found");
        return o;
    }

    private String generateOrderNumber() {
        return "ORD" + LocalDateTime.now().format(NUMBER_DATE) + String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
