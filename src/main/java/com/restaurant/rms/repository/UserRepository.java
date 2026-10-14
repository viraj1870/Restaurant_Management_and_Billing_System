package com.restaurant.rms.repository;

import com.restaurant.rms.model.Role;
import com.restaurant.rms.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByMobile(String mobile);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByMobile(String mobile);
    List<User> findByRestaurantIdAndRole(Long restaurantId, Role role);
}
