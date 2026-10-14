package com.restaurant.rms.service.notification;

import com.restaurant.rms.config.AppProperties;
import com.restaurant.rms.model.*;
import com.restaurant.rms.repository.NotificationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends order/payment updates by email and SMS and records every attempt in notification_logs.
 * <ul>
 *   <li>Email is sent through Spring Mail when {@code app.notification.email-enabled=true} and SMTP is configured,
 *       otherwise it is logged as SIMULATED.</li>
 *   <li>SMS uses the "log" provider by default (SIMULATED) so no paid credentials are required for the demo.
 *       Plug a real provider into {@link #sendSms}.</li>
 *   <li>Customers who have not given notification consent are SKIPPED.</li>
 * </ul>
 * Notification failures never break the business operation.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationLogRepository repository;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final AppProperties props;

    public NotificationService(NotificationLogRepository repository, ObjectProvider<JavaMailSender> mailSender,
                               AppProperties props) {
        this.repository = repository;
        this.mailSender = mailSender;
        this.props = props;
    }

    public void orderPlaced(Order o) {
        notifyCustomer(o, "Order " + o.getOrderNumber() + " placed",
                "Hi " + o.getCustomer().getName() + ", your order " + o.getOrderNumber() + " at "
                        + o.getRestaurant().getName() + " for Rs." + o.getTotalAmount() + " has been placed.");
    }

    public void orderStatusChanged(Order o) {
        String text = switch (o.getStatus()) {
            case PREPARING -> "is being prepared in the kitchen.";
            case READY -> "is ready! Please collect it / it will be served shortly.";
            case COMPLETED -> "is completed. Thank you for dining with us! Please share your feedback.";
            case CANCELLED -> "has been cancelled.";
            default -> "status is now " + o.getStatus() + ".";
        };
        notifyCustomer(o, "Order " + o.getOrderNumber() + " " + o.getStatus().name().toLowerCase(),
                "Your order " + o.getOrderNumber() + " " + text);
    }

    public void paymentUpdate(Order o, Payment p) {
        String text = p.getStatus() == TransactionStatus.SUCCESS
                ? "Payment of Rs." + p.getAmount() + " received for order " + o.getOrderNumber() + " via " + p.getMethod()
                        + ". Ref: " + p.getTransactionRef()
                : "Payment for order " + o.getOrderNumber() + " failed: " + p.getFailureReason();
        notifyCustomer(o, "Payment " + p.getStatus().name().toLowerCase() + " - " + o.getOrderNumber(), text);
    }

    private void notifyCustomer(Order order, String subject, String message) {
        User c = order.getCustomer();
        try {
            send(c, order, NotificationChannel.EMAIL, c.getEmail(), subject, message);
            send(c, order, NotificationChannel.SMS, c.getMobile(), subject, message);
        } catch (RuntimeException e) {
            log.warn("Notification failed for order {}: {}", order.getOrderNumber(), e.getMessage());
        }
    }

    private void send(User user, Order order, NotificationChannel channel, String recipient, String subject, String message) {
        NotificationLog entry = new NotificationLog();
        entry.setUser(user);
        entry.setOrder(order);
        entry.setChannel(channel);
        entry.setRecipient(recipient);
        entry.setSubject(subject);
        entry.setMessage(message);

        if (!user.isNotificationConsent()) {
            entry.setStatus(NotificationStatus.SKIPPED);
            entry.setErrorMessage("Customer has not consented to notifications");
        } else {
            try {
                entry.setStatus(channel == NotificationChannel.EMAIL
                        ? sendEmail(recipient, subject, message)
                        : sendSms(recipient, message));
            } catch (RuntimeException e) {
                entry.setStatus(NotificationStatus.FAILED);
                entry.setErrorMessage(truncate(e.getMessage(), 500));
            }
        }
        repository.save(entry);
    }

    private NotificationStatus sendEmail(String to, String subject, String body) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (!props.getNotification().isEmailEnabled() || sender == null) {
            log.info("[EMAIL-SIMULATED] to={} subject='{}' body='{}'", to, subject, body);
            return NotificationStatus.SIMULATED;
        }
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(props.getNotification().getFrom());
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(body);
        sender.send(msg);
        return NotificationStatus.SENT;
    }

    private NotificationStatus sendSms(String mobile, String body) {
        // Integration point for an SMS gateway (e.g. Twilio / MSG91). Credentials must come from environment variables.
        log.info("[SMS-SIMULATED via {}] to={} body='{}'", props.getNotification().getSmsProvider(), mobile, body);
        return NotificationStatus.SIMULATED;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
