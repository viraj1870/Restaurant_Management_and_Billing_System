package com.restaurant.rms.controller;

import com.restaurant.rms.dto.FeedbackDtos.FeedbackDto;
import com.restaurant.rms.dto.FeedbackDtos.FeedbackRequest;
import com.restaurant.rms.dto.FeedbackDtos.NotificationDto;
import com.restaurant.rms.dto.OfferDtos.LoyaltySummary;
import com.restaurant.rms.dto.OrderDtos.OrderDto;
import com.restaurant.rms.dto.ReportDtos.DailyReport;
import com.restaurant.rms.dto.ReportDtos.FeedbackReport;
import com.restaurant.rms.dto.ReportDtos.SalesReport;
import com.restaurant.rms.model.Role;
import com.restaurant.rms.repository.NotificationLogRepository;
import com.restaurant.rms.service.*;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

public final class MiscControllers {

    private MiscControllers() {}

    @RestController
    @RequestMapping("/api/feedback")
    public static class FeedbackController {
        private final FeedbackService feedbackService;

        public FeedbackController(FeedbackService feedbackService) { this.feedbackService = feedbackService; }

        @PostMapping
        @PreAuthorize("hasRole('CUSTOMER')")
        public ResponseEntity<FeedbackDto> submit(@Valid @RequestBody FeedbackRequest req) {
            return ResponseEntity.status(HttpStatus.CREATED).body(feedbackService.submit(req));
        }

        @GetMapping("/my")
        @PreAuthorize("hasRole('CUSTOMER')")
        public List<FeedbackDto> mine() { return feedbackService.mine(); }
    }

    @RestController
    @RequestMapping("/api/reports")
    public static class ReportController {
        private final ReportService reportService;

        public ReportController(ReportService reportService) { this.reportService = reportService; }

        @GetMapping("/daily")
        public DailyReport daily(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
            return reportService.daily(date);
        }

        @GetMapping("/sales")
        public SalesReport sales(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
            return reportService.sales(from, to);
        }

        @GetMapping("/feedback")
        public FeedbackReport feedback() { return reportService.feedback(); }
    }

    @RestController
    @RequestMapping("/api/kitchen")
    public static class KitchenController {
        private final OrderService orderService;

        public KitchenController(OrderService orderService) { this.orderService = orderService; }

        @GetMapping("/orders")
        public List<OrderDto> orders() { return orderService.kitchenOrders(); }
    }

    @RestController
    @RequestMapping("/api/customer")
    public static class CustomerController {
        private final LoyaltyService loyaltyService;
        private final CurrentUserService currentUser;
        private final NotificationLogRepository notificationLogRepository;
        private final DtoMapper mapper;

        public CustomerController(LoyaltyService loyaltyService, CurrentUserService currentUser,
                                  NotificationLogRepository notificationLogRepository, DtoMapper mapper) {
            this.loyaltyService = loyaltyService;
            this.currentUser = currentUser;
            this.notificationLogRepository = notificationLogRepository;
            this.mapper = mapper;
        }

        @GetMapping("/loyalty")
        public LoyaltySummary loyalty() {
            return loyaltyService.summary(currentUser.requireRole(Role.CUSTOMER));
        }

        @GetMapping("/notifications")
        @org.springframework.transaction.annotation.Transactional(readOnly = true)
        public List<NotificationDto> notifications() {
            return notificationLogRepository.findTop50ByUserIdOrderByCreatedAtDesc(currentUser.principal().getId())
                    .stream().map(mapper::notification).toList();
        }
    }
}
