package com.restaurant.rms.repository;

import com.restaurant.rms.model.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {
    Optional<Coupon> findByRestaurantIdAndCodeIgnoreCase(Long restaurantId, String code);
    Optional<Coupon> findByIdAndRestaurantId(Long id, Long restaurantId);
    List<Coupon> findByRestaurantIdOrderByIdDesc(Long restaurantId);
    boolean existsByRestaurantIdAndCodeIgnoreCase(Long restaurantId, String code);
}
