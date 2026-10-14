package com.restaurant.rms.repository;

import com.restaurant.rms.model.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {
    Optional<Restaurant> findByOwnerId(Long ownerId);
    List<Restaurant> findByActiveTrueOrderByNameAsc();
}
