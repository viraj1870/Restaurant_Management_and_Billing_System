package com.restaurant.rms.repository;

import com.restaurant.rms.model.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
    List<NotificationLog> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);
    List<NotificationLog> findTop100ByOrderRestaurantIdOrderByCreatedAtDesc(Long restaurantId);
}
