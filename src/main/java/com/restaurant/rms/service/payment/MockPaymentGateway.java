package com.restaurant.rms.service.payment;

import com.restaurant.rms.config.AppProperties;
import com.restaurant.rms.dto.OrderDtos.PaymentRequest;
import com.restaurant.rms.model.PaymentMethod;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Sandbox gateway used for the demo. It behaves like a real gateway: every response is HMAC-SHA256 signed
 * and {@link #verify} checks the signature before the payment is marked successful.
 * <p>Test rules (similar to real sandbox gateways):</p>
 * <ul>
 *   <li>CARD: number must pass the Luhn check; card numbers ending in <b>0002</b> are declined.</li>
 *   <li>UPI: id must look like name@bank; ids starting with <b>fail</b> are declined.</li>
 *   <li>NET_BANKING: a bank code is required; code <b>FAIL</b> is declined.</li>
 * </ul>
 * Only the last 4 digits of a card are ever stored or logged.
 */
@Component
public class MockPaymentGateway implements PaymentGateway {

    private final byte[] secret;

    public MockPaymentGateway(AppProperties props) {
        this.secret = props.getPayment().getGatewaySecret().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String name() { return "MOCK_GATEWAY"; }

    @Override
    public GatewayResponse charge(String orderNumber, BigDecimal amount, PaymentRequest req) {
        String failure = validate(req);
        boolean success = failure == null;
        String ref = (success ? "TXN" : "FAIL") + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        return new GatewayResponse(success, ref, failure, sign(orderNumber, amount, ref, success));
    }

    @Override
    public boolean verify(String orderNumber, BigDecimal amount, GatewayResponse r) {
        if (r == null || r.signature() == null) return false;
        String expected = sign(orderNumber, amount, r.transactionRef(), r.success());
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), r.signature().getBytes(StandardCharsets.UTF_8));
    }

    private String validate(PaymentRequest req) {
        PaymentMethod m = req.method();
        return switch (m) {
            case CARD -> {
                String digits = req.cardNumber() == null ? "" : req.cardNumber().replaceAll("[\\s-]", "");
                if (!digits.matches("\\d{12,19}") || !luhn(digits)) yield "Invalid card number";
                if (digits.endsWith("0002")) yield "Card declined by issuing bank";
                yield null;
            }
            case UPI -> {
                String upi = req.upiId() == null ? "" : req.upiId().trim();
                if (!upi.matches("^[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z]{2,64}$")) yield "Invalid UPI ID";
                if (upi.toLowerCase().startsWith("fail")) yield "UPI transaction declined";
                yield null;
            }
            case NET_BANKING -> {
                String bank = req.bankCode() == null ? "" : req.bankCode().trim();
                if (bank.isEmpty()) yield "Please select a bank";
                if (bank.equalsIgnoreCase("FAIL")) yield "Bank authentication failed";
                yield null;
            }
            case CASH -> "Cash payments must be recorded at the counter";
        };
    }

    private String sign(String orderNumber, BigDecimal amount, String ref, boolean success) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            String payload = orderNumber + "|" + amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
                    + "|" + ref + "|" + success;
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign gateway response", e);
        }
    }

    static boolean luhn(String digits) {
        int sum = 0;
        boolean alt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int n = digits.charAt(i) - '0';
            if (alt) {
                n *= 2;
                if (n > 9) n -= 9;
            }
            sum += n;
            alt = !alt;
        }
        return sum % 10 == 0;
    }
}
