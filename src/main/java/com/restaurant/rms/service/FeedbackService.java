package com.restaurant.rms.service;

import com.restaurant.rms.dto.FeedbackDtos.*;
import com.restaurant.rms.exception.ApiException;
import com.restaurant.rms.model.*;
import com.restaurant.rms.repository.FeedbackRepository;
import com.restaurant.rms.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final OrderRepository orderRepository;
    private final RestaurantService restaurantService;
    private final CurrentUserService currentUser;
    private final DtoMapper mapper;

    public FeedbackService(FeedbackRepository feedbackRepository, OrderRepository orderRepository,
                           RestaurantService restaurantService, CurrentUserService currentUser, DtoMapper mapper) {
        this.feedbackRepository = feedbackRepository;
        this.orderRepository = orderRepository;
        this.restaurantService = restaurantService;
        this.currentUser = currentUser;
        this.mapper = mapper;
    }

    /**
     * Reviews (rating + comment) are allowed once per completed order.
     * Complaints can be raised about any of the customer's orders, or about the restaurant in general.
     */
    @Transactional
    public FeedbackDto submit(FeedbackRequest req) {
        User customer = currentUser.requireRole(Role.CUSTOMER);
        FeedbackType type = req.type() == null ? FeedbackType.REVIEW : req.type();

        Order order = null;
        Restaurant restaurant;
        if (req.orderId() != null) {
            order = orderRepository.findById(req.orderId())
                    .filter(o -> o.getCustomer().getId().equals(customer.getId()))
                    .orElseThrow(() -> ApiException.notFound("Order not found"));
            restaurant = order.getRestaurant();
        } else if (req.restaurantId() != null) {
            restaurant = restaurantService.activeRestaurant(req.restaurantId());
        } else {
            throw ApiException.badRequest("Order or restaurant is required");
        }

        if (type == FeedbackType.REVIEW) {
            if (order == null) throw ApiException.badRequest("Reviews must be linked to an order");
            if (order.getStatus() != OrderStatus.COMPLETED) {
                throw ApiException.badRequest("You can review an order only after it is completed");
            }
            if (feedbackRepository.existsByOrderIdAndType(order.getId(), FeedbackType.REVIEW)) {
                throw ApiException.conflict("You have already reviewed this order");
            }
            if (req.rating() == null) throw ApiException.badRequest("Rating is required");
        } else if (req.comment() == null || req.comment().isBlank()) {
            throw ApiException.badRequest("Please describe your complaint");
        }

        Feedback f = new Feedback();
        f.setCustomer(customer);
        f.setRestaurant(restaurant);
        f.setOrder(order);
        f.setType(type);
        f.setRating(req.rating());
        f.setComment(req.comment() == null ? null : req.comment().trim());
        f.setStatus(type == FeedbackType.REVIEW ? FeedbackStatus.RESOLVED : FeedbackStatus.OPEN);
        return mapper.feedback(feedbackRepository.save(f));
    }

    public List<FeedbackDto> mine() {
        User customer = currentUser.requireRole(Role.CUSTOMER);
        return feedbackRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId()).stream().map(mapper::feedback).toList();
    }

    public List<FeedbackDto> forMyRestaurant() {
        Restaurant r = currentUser.merchantRestaurant();
        return feedbackRepository.findByRestaurantIdOrderByCreatedAtDesc(r.getId()).stream().map(mapper::feedback).toList();
    }

    /** Public reviews shown on the restaurant page (complaints are private). */
    public List<FeedbackDto> publicReviews(Long restaurantId) {
        return feedbackRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId).stream()
                .filter(f -> f.getType() == FeedbackType.REVIEW)
                .limit(20).map(mapper::feedback).toList();
    }

    @Transactional
    public FeedbackDto respond(Long id, FeedbackUpdateRequest req) {
        Restaurant r = currentUser.merchantRestaurant();
        Feedback f = feedbackRepository.findByIdAndRestaurantId(id, r.getId())
                .orElseThrow(() -> ApiException.notFound("Feedback not found"));
        if (req.reply() != null && !req.reply().isBlank()) f.setMerchantReply(req.reply().trim());
        if (req.status() != null) {
            f.setStatus(req.status());
            f.setResolvedAt(req.status() == FeedbackStatus.RESOLVED ? LocalDateTime.now() : null);
        }
        return mapper.feedback(f);
    }
}
