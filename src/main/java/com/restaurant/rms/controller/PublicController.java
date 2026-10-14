package com.restaurant.rms.controller;

import com.restaurant.rms.dto.FeedbackDtos.FeedbackDto;
import com.restaurant.rms.dto.MenuDtos.MenuResponse;
import com.restaurant.rms.dto.MenuDtos.RestaurantDto;
import com.restaurant.rms.dto.OfferDtos.CouponDto;
import com.restaurant.rms.exception.ApiException;
import com.restaurant.rms.service.CouponService;
import com.restaurant.rms.service.FeedbackService;
import com.restaurant.rms.service.RestaurantService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Endpoints available without login: restaurant list, menu, reviews. */
@RestController
public class PublicController {

    private final RestaurantService restaurantService;
    private final FeedbackService feedbackService;
    private final CouponService couponService;

    public PublicController(RestaurantService restaurantService, FeedbackService feedbackService,
                            CouponService couponService) {
        this.restaurantService = restaurantService;
        this.feedbackService = feedbackService;
        this.couponService = couponService;
    }

    @GetMapping("/api/restaurants")
    public List<RestaurantDto> restaurants() {
        return restaurantService.listActive();
    }

    @GetMapping("/api/restaurants/{id}")
    public RestaurantDto restaurant(@PathVariable Long id) {
        return restaurantService.getPublic(id);
    }

    /** GET /api/menu?restaurantId=1 - if only one restaurant exists the id can be omitted. */
    @GetMapping("/api/menu")
    public MenuResponse menu(@RequestParam(required = false) Long restaurantId) {
        if (restaurantId == null) {
            List<RestaurantDto> all = restaurantService.listActive();
            if (all.isEmpty()) throw ApiException.notFound("No restaurants available");
            restaurantId = all.get(0).id();
        }
        return restaurantService.publicMenu(restaurantId);
    }

    @GetMapping("/api/menu/{restaurantId}/reviews")
    public List<FeedbackDto> reviews(@PathVariable Long restaurantId) {
        restaurantService.activeRestaurant(restaurantId);
        return feedbackService.publicReviews(restaurantId);
    }

    @GetMapping("/api/menu/{restaurantId}/coupons")
    public List<CouponDto> coupons(@PathVariable Long restaurantId) {
        restaurantService.activeRestaurant(restaurantId);
        return couponService.activeForRestaurant(restaurantId);
    }
}
