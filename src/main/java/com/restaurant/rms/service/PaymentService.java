package com.restaurant.rms.service;

import com.restaurant.rms.dto.OrderDtos.*;
import com.restaurant.rms.exception.ApiException;
import com.restaurant.rms.model.*;
import com.restaurant.rms.repository.PaymentRepository;
import com.restaurant.rms.service.notification.NotificationService;
import com.restaurant.rms.service.payment.PaymentGateway;
import com.restaurant.rms.service.payment.PaymentGateway.GatewayResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderService orderService;
    private final PaymentGateway gateway;
    private final NotificationService notificationService;
    private final RestaurantService restaurantService;
    private final CurrentUserService currentUser;
    private final DtoMapper mapper;

    public PaymentService(PaymentRepository paymentRepository, OrderService orderService, PaymentGateway gateway,
                          NotificationService notificationService, RestaurantService restaurantService,
                          CurrentUserService currentUser, DtoMapper mapper) {
        this.paymentRepository = paymentRepository;
        this.orderService = orderService;
        this.gateway = gateway;
        this.notificationService = notificationService;
        this.restaurantService = restaurantService;
        this.currentUser = currentUser;
        this.mapper = mapper;
    }

    /**
     * Customers pay online (card/UPI/net banking) through the gateway; the response signature is verified
     * before the order is marked PAID. Merchants record counter payments (cash or card/UPI swiped at the counter).
     */
    @Transactional(noRollbackFor = ApiException.class)
    public PaymentDto pay(Long orderId, PaymentRequest req) {
        User actor = currentUser.user();
        Order o = orderService.accessibleOrder(orderId);
        if (actor.getRole() == Role.KITCHEN) throw ApiException.forbidden("Kitchen staff cannot record payments");
        if (o.getStatus() == OrderStatus.CANCELLED) throw ApiException.badRequest("Cannot pay for a cancelled order");
        if (o.getPaymentStatus() == PaymentStatus.PAID || paymentRepository.existsByOrderIdAndStatus(o.getId(), TransactionStatus.SUCCESS)) {
            throw ApiException.conflict("This order is already paid");
        }

        Payment p = new Payment();
        p.setOrder(o);
        p.setMethod(req.method());
        p.setAmount(o.getTotalAmount());
        p.setRecordedBy(actor);

        if (actor.getRole() == Role.MERCHANT) {
            // A pending "pay at counter" request from the customer is settled instead of creating a duplicate.
            Payment pending = paymentRepository.findByOrderIdOrderByCreatedAtDesc(o.getId()).stream()
                    .filter(x -> x.getStatus() == TransactionStatus.PENDING).findFirst().orElse(null);
            if (pending != null) {
                pending.setMethod(req.method());
                pending.setStatus(TransactionStatus.SUCCESS);
                pending.setRecordedBy(actor);
                o.setPaymentStatus(PaymentStatus.PAID);
                notificationService.paymentUpdate(o, pending);
                return mapper.payment(pending);
            }
            p.setGateway("COUNTER");
            p.setStatus(TransactionStatus.SUCCESS);
            p.setTransactionRef("CNT" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        } else {
            if (req.method() == PaymentMethod.CASH) {
                if (o.getPaymentStatus() == PaymentStatus.PENDING) {
                    throw ApiException.conflict("Pay-at-counter is already requested for this order");
                }
                p.setGateway("COUNTER");
                p.setStatus(TransactionStatus.PENDING);
                p.setTransactionRef("CSH" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
                paymentRepository.save(p);
                o.setPaymentStatus(PaymentStatus.PENDING);
                return mapper.payment(p);
            }
            GatewayResponse resp = gateway.charge(o.getOrderNumber(), o.getTotalAmount(), req);
            p.setGateway(gateway.name());
            p.setTransactionRef(resp.transactionRef());
            if (!gateway.verify(o.getOrderNumber(), o.getTotalAmount(), resp)) {
                p.setStatus(TransactionStatus.FAILED);
                p.setFailureReason("Gateway signature verification failed");
            } else if (resp.success()) {
                p.setStatus(TransactionStatus.SUCCESS);
            } else {
                p.setStatus(TransactionStatus.FAILED);
                p.setFailureReason(resp.failureReason());
            }
        }
        paymentRepository.save(p);

        if (p.getStatus() == TransactionStatus.SUCCESS) {
            paymentRepository.findByOrderIdOrderByCreatedAtDesc(o.getId()).stream()
                    .filter(x -> x.getStatus() == TransactionStatus.PENDING && x != p)
                    .forEach(x -> {
                        x.setStatus(TransactionStatus.FAILED);
                        x.setFailureReason("Superseded by online payment");
                    });
            o.setPaymentStatus(PaymentStatus.PAID);
            if (o.getStatus() == OrderStatus.COMPLETED) orderService.awardPoints(o);
        }
        notificationService.paymentUpdate(o, p);
        return mapper.payment(p);
    }

    public List<PaymentDto> forOrder(Long orderId) {
        Order o = orderService.accessibleOrder(orderId);
        return paymentRepository.findByOrderIdOrderByCreatedAtDesc(o.getId()).stream().map(mapper::payment).toList();
    }

    public List<PaymentDto> restaurantPayments() {
        Restaurant r = currentUser.merchantRestaurant();
        return paymentRepository.findByOrderRestaurantIdOrderByCreatedAtDesc(r.getId()).stream().map(mapper::payment).toList();
    }

    public ReceiptDto receipt(Long orderId) {
        Order o = orderService.accessibleOrder(orderId);
        return new ReceiptDto(restaurantService.toDto(o.getRestaurant()), mapper.order(o),
                paymentRepository.findByOrderIdOrderByCreatedAtDesc(o.getId()).stream().map(mapper::payment).toList(),
                LocalDateTime.now());
    }
}
