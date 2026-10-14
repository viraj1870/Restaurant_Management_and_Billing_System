package com.restaurant.rms.service.payment;

import com.restaurant.rms.dto.OrderDtos.PaymentRequest;

import java.math.BigDecimal;

/** Abstraction over an online payment gateway (Razorpay, Stripe, ...). */
public interface PaymentGateway {

    /** Signed response from the gateway. The signature must be verified before trusting the status. */
    record GatewayResponse(boolean success, String transactionRef, String failureReason, String signature) {}

    String name();

    GatewayResponse charge(String orderNumber, BigDecimal amount, PaymentRequest request);

    boolean verify(String orderNumber, BigDecimal amount, GatewayResponse response);
}
