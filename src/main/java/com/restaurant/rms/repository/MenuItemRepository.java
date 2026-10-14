package com.restaurant.rms.repository;

import com.restaurant.rms.model.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {
    List<MenuItem> findByRestaurantIdAndDeletedFalseOrderByNameAsc(Long restaurantId);
    Optional<MenuItem> findByIdAndRestaurantIdAndDeletedFalse(Long id, Long restaurantId);
    long countByCategoryIdAndDeletedFalse(Long categoryId);
}
