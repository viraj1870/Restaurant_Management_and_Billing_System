package com.restaurant.rms.repository;

import com.restaurant.rms.model.Payment;
import com.restaurant.rms.model.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByOrderIdOrderByCreatedAtDesc(Long orderId);
    List<Payment> findByOrderRestaurantIdOrderByCreatedAtDesc(Long restaurantId);
    List<Payment> findByOrderRestaurantIdAndStatusAndCreatedAtBetween(
            Long restaurantId, TransactionStatus status, LocalDateTime from, LocalDateTime to);
    boolean existsByOrderIdAndStatus(Long orderId, TransactionStatus status);
}
