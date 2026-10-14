package com.restaurant.rms.repository;

import com.restaurant.rms.model.Feedback;
import com.restaurant.rms.model.FeedbackType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    List<Feedback> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId);
    List<Feedback> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    Optional<Feedback> findByIdAndRestaurantId(Long id, Long restaurantId);
    boolean existsByOrderIdAndType(Long orderId, FeedbackType type);

    @Query("""
            select avg(f.rating) as average, count(f.rating) as count from Feedback f
            where f.restaurant.id = :restaurantId and f.rating is not null
              and f.type = com.restaurant.rms.model.FeedbackType.REVIEW
            """)
    RatingView ratingSummary(@Param("restaurantId") Long restaurantId);

    interface RatingView {
        Double getAverage();
        Long getCount();
    }
}