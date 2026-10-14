package com.restaurant.rms.service;

import com.restaurant.rms.exception.ApiException;
import com.restaurant.rms.model.Restaurant;
import com.restaurant.rms.model.Role;
import com.restaurant.rms.model.User;
import com.restaurant.rms.repository.RestaurantRepository;
import com.restaurant.rms.repository.UserRepository;
import com.restaurant.rms.security.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** Resolves the signed-in user and enforces restaurant ownership. */
@Service
public class CurrentUserService {

    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;

    public CurrentUserService(UserRepository userRepository, RestaurantRepository restaurantRepository) {
        this.userRepository = userRepository;
        this.restaurantRepository = restaurantRepository;
    }

    public UserPrincipal principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal p)) {
            throw ApiException.unauthorized("Authentication required");
        }
        return p;
    }

    public User user() {
        return userRepository.findById(principal().getId())
                .orElseThrow(() -> ApiException.unauthorized("User no longer exists"));
    }

    public User requireRole(Role role) {
        User u = user();
        if (u.getRole() != role) {
            throw ApiException.forbidden("This action requires the " + role.name().toLowerCase() + " role");
        }
        return u;
    }

    /** Restaurant owned by the current merchant. */
    public Restaurant merchantRestaurant() {
        User merchant = requireRole(Role.MERCHANT);
        return restaurantRepository.findByOwnerId(merchant.getId())
                .orElseThrow(() -> ApiException.notFound("No restaurant linked to this merchant account"));
    }

    /** Restaurant the current merchant owns or the kitchen staff member works for. */
    public Restaurant staffRestaurant() {
        User u = user();
        return switch (u.getRole()) {
            case MERCHANT -> merchantRestaurant();
            case KITCHEN -> {
                if (u.getRestaurant() == null) throw ApiException.forbidden("Kitchen account is not linked to a restaurant");
                yield u.getRestaurant();
            }
            default -> throw ApiException.forbidden("Only restaurant staff can perform this action");
        };
    }
}
