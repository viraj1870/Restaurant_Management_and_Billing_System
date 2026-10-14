package com.restaurant.rms.controller;

import com.restaurant.rms.dto.OrderDtos.*;
import com.restaurant.rms.service.OrderService;
import com.restaurant.rms.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final PaymentService paymentService;

    public OrderController(OrderService orderService, PaymentService paymentService) {
        this.orderService = orderService;
        this.paymentService = paymentService;
    }

    @PostMapping("/preview")
    @PreAuthorize("hasRole('CUSTOMER')")
    public BillPreview preview(@Valid @RequestBody OrderRequest req) {
        return orderService.preview(req);
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<OrderDto> create(@Valid @RequestBody OrderRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.placeOrder(req));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<OrderDto> myOrders() {
        return orderService.myOrders();
    }

    @GetMapping("/{id}")
    public OrderDto get(@PathVariable Long id) {
        return orderService.get(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public OrderDto cancel(@PathVariable Long id) {
        return orderService.cancelByCustomer(id);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('MERCHANT','KITCHEN')")
    public OrderDto updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest req) {
        return orderService.updateStatus(id, req.status());
    }

    @PostMapping("/{id}/payments")
    @PreAuthorize("hasAnyRole('CUSTOMER','MERCHANT')")
    public PaymentDto pay(@PathVariable Long id, @Valid @RequestBody PaymentRequest req) {
        return paymentService.pay(id, req);
    }

    @GetMapping("/{id}/payments")
    public List<PaymentDto> payments(@PathVariable Long id) {
        return paymentService.forOrder(id);
    }

    @GetMapping("/{id}/receipt")
    @PreAuthorize("hasAnyRole('CUSTOMER','MERCHANT')")
    public ReceiptDto receipt(@PathVariable Long id) {
        return paymentService.receipt(id);
    }
}
