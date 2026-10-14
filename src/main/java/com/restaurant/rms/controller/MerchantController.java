package com.restaurant.rms.controller;

import com.restaurant.rms.dto.AuthDtos.StaffDto;
import com.restaurant.rms.dto.AuthDtos.StaffRequest;
import com.restaurant.rms.dto.FeedbackDtos.*;
import com.restaurant.rms.dto.MenuDtos.*;
import com.restaurant.rms.dto.OfferDtos.CouponDto;
import com.restaurant.rms.dto.OfferDtos.CouponRequest;
import com.restaurant.rms.dto.OrderDtos.OrderDto;
import com.restaurant.rms.dto.OrderDtos.PaymentDto;
import com.restaurant.rms.model.OrderStatus;
import com.restaurant.rms.repository.NotificationLogRepository;
import com.restaurant.rms.service.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Restaurant owner operations. Every method is scoped to the merchant's own restaurant. */
@RestController
@RequestMapping("/api/merchant")
public class MerchantController {

    private final RestaurantService restaurantService;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final CouponService couponService;
    private final FeedbackService feedbackService;
    private final ReportService reportService;
    private final AuthService authService;
    private final CurrentUserService currentUser;
    private final NotificationLogRepository notificationLogRepository;
    private final DtoMapper mapper;

    public MerchantController(RestaurantService restaurantService, OrderService orderService,
                              PaymentService paymentService, CouponService couponService,
                              FeedbackService feedbackService, ReportService reportService, AuthService authService,
                              CurrentUserService currentUser, NotificationLogRepository notificationLogRepository,
                              DtoMapper mapper) {
        this.restaurantService = restaurantService;
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.couponService = couponService;
        this.feedbackService = feedbackService;
        this.reportService = reportService;
        this.authService = authService;
        this.currentUser = currentUser;
        this.notificationLogRepository = notificationLogRepository;
        this.mapper = mapper;
    }

    // ----- Restaurant profile -----
    @GetMapping("/restaurant")
    public RestaurantDto restaurant() { return restaurantService.myRestaurant(); }

    @PutMapping("/restaurant")
    public RestaurantDto updateRestaurant(@Valid @RequestBody RestaurantUpdateRequest req) {
        return restaurantService.updateMyRestaurant(req);
    }

    // ----- Categories -----
    @GetMapping("/categories")
    public List<CategoryDto> categories() { return restaurantService.myCategories(); }

    @PostMapping("/categories")
    public ResponseEntity<CategoryDto> createCategory(@Valid @RequestBody CategoryRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(restaurantService.createCategory(req));
    }

    @PutMapping("/categories/{id}")
    public CategoryDto updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest req) {
        return restaurantService.updateCategory(id, req);
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        restaurantService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    // ----- Menu items -----
    @GetMapping("/menu-items")
    public List<MenuItemDto> menuItems() { return restaurantService.myMenuItems(); }

    @PostMapping("/menu-items")
    public ResponseEntity<MenuItemDto> createMenuItem(@Valid @RequestBody MenuItemRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(restaurantService.createMenuItem(req));
    }

    @PutMapping("/menu-items/{id}")
    public MenuItemDto updateMenuItem(@PathVariable Long id, @Valid @RequestBody MenuItemRequest req) {
        return restaurantService.updateMenuItem(id, req);
    }

    @PatchMapping("/menu-items/{id}/availability")
    public MenuItemDto availability(@PathVariable Long id, @Valid @RequestBody AvailabilityRequest req) {
        return restaurantService.setAvailability(id, req.available());
    }

    @DeleteMapping("/menu-items/{id}")
    public ResponseEntity<Void> deleteMenuItem(@PathVariable Long id) {
        restaurantService.deleteMenuItem(id);
        return ResponseEntity.noContent().build();
    }

    // ----- Orders & payments -----
    @GetMapping("/orders")
    public List<OrderDto> orders(@RequestParam(required = false) OrderStatus status) {
        return orderService.restaurantOrders(status);
    }

    @GetMapping("/payments")
    public List<PaymentDto> payments() { return paymentService.restaurantPayments(); }

    // ----- Coupons -----
    @GetMapping("/coupons")
    public List<CouponDto> coupons() { return couponService.myCoupons(); }

    @PostMapping("/coupons")
    public ResponseEntity<CouponDto> createCoupon(@Valid @RequestBody CouponRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(couponService.create(req));
    }

    @PutMapping("/coupons/{id}")
    public CouponDto updateCoupon(@PathVariable Long id, @Valid @RequestBody CouponRequest req) {
        return couponService.update(id, req);
    }

    @DeleteMapping("/coupons/{id}")
    public ResponseEntity<Void> deleteCoupon(@PathVariable Long id) {
        couponService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ----- Customers, feedback, notifications -----
    @GetMapping("/customers")
    public List<CustomerSummaryDto> customers() { return reportService.customers(); }

    @GetMapping("/feedback")
    public List<FeedbackDto> feedback() { return feedbackService.forMyRestaurant(); }

    @PutMapping("/feedback/{id}")
    public FeedbackDto respond(@PathVariable Long id, @Valid @RequestBody FeedbackUpdateRequest req) {
        return feedbackService.respond(id, req);
    }

    @GetMapping("/notifications")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<NotificationDto> notifications() {
        Long rid = currentUser.merchantRestaurant().getId();
        return notificationLogRepository.findTop100ByOrderRestaurantIdOrderByCreatedAtDesc(rid).stream()
                .map(mapper::notification).toList();
    }

    // ----- Kitchen staff -----
    @GetMapping("/staff")
    public List<StaffDto> staff() { return authService.listStaff(); }

    @PostMapping("/staff")
    public ResponseEntity<StaffDto> createStaff(@Valid @RequestBody StaffRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.createStaff(req));
    }

    @PatchMapping("/staff/{id}")
    public StaffDto setStaffEnabled(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        return authService.setStaffEnabled(id, Boolean.TRUE.equals(body.get("enabled")));
    }
}
