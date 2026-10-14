package com.restaurant.rms.service;

import com.restaurant.rms.dto.FeedbackDtos.CustomerSummaryDto;
import com.restaurant.rms.dto.ReportDtos.*;
import com.restaurant.rms.exception.ApiException;
import com.restaurant.rms.model.*;
import com.restaurant.rms.repository.FeedbackRepository;
import com.restaurant.rms.repository.OrderRepository;
import com.restaurant.rms.repository.PaymentRepository;
import com.restaurant.rms.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ReportService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUser;

    public ReportService(OrderRepository orderRepository, PaymentRepository paymentRepository,
                         FeedbackRepository feedbackRepository, UserRepository userRepository,
                         CurrentUserService currentUser) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.feedbackRepository = feedbackRepository;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
    }

    public DailyReport daily(LocalDate date) {
        Restaurant r = currentUser.merchantRestaurant();
        LocalDate day = date == null ? LocalDate.now() : date;
        LocalDateTime from = day.atStartOfDay();
        LocalDateTime to = day.plusDays(1).atStartOfDay().minusNanos(1);

        List<Order> orders = orderRepository.findByRestaurantIdAndCreatedAtBetween(r.getId(), from, to);
        List<Order> valid = orders.stream().filter(o -> o.getStatus() != OrderStatus.CANCELLED).toList();

        BigDecimal gross = sum(valid, Order::getTotalAmount);
        BigDecimal tax = sum(valid, Order::getTaxAmount);
        BigDecimal discounts = sum(valid, Order::getCouponDiscount).add(sum(valid, Order::getLoyaltyDiscount));
        BigDecimal pending = sum(valid.stream().filter(o -> o.getPaymentStatus() != PaymentStatus.PAID).toList(), Order::getTotalAmount);

        List<Payment> payments = paymentRepository.findByOrderRestaurantIdAndStatusAndCreatedAtBetween(
                r.getId(), TransactionStatus.SUCCESS, from, to);
        BigDecimal collected = payments.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, BigDecimal> byMethod = new TreeMap<>();
        for (Payment p : payments) byMethod.merge(p.getMethod().name(), p.getAmount(), BigDecimal::add);

        Map<Integer, Long> byHour = new TreeMap<>();
        for (Order o : valid) byHour.merge(o.getCreatedAt().getHour(), 1L, Long::sum);

        return new DailyReport(day, orders.size(),
                count(orders, OrderStatus.COMPLETED), count(orders, OrderStatus.CANCELLED),
                orders.stream().filter(o -> OrderStatus.KITCHEN_ACTIVE.contains(o.getStatus())).count(),
                gross, collected, pending, tax, discounts, avg(gross, valid.size()), byMethod, byHour,
                popular(r.getId(), from, to, 10));
    }

    public SalesReport sales(LocalDate from, LocalDate to) {
        Restaurant r = currentUser.merchantRestaurant();
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(6) : from;
        if (start.isAfter(end)) throw ApiException.badRequest("'from' date must be before 'to' date");
        if (start.plusDays(366).isBefore(end)) throw ApiException.badRequest("Date range cannot exceed one year");

        LocalDateTime f = start.atStartOfDay();
        LocalDateTime t = end.plusDays(1).atStartOfDay().minusNanos(1);
        List<Order> orders = orderRepository.findByRestaurantIdAndCreatedAtBetween(r.getId(), f, t);
        List<Order> valid = orders.stream().filter(o -> o.getStatus() != OrderStatus.CANCELLED).toList();

        Map<LocalDate, List<Order>> byDay = valid.stream().collect(Collectors.groupingBy(o -> o.getCreatedAt().toLocalDate()));
        List<DaySales> daily = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            List<Order> list = byDay.getOrDefault(d, List.of());
            daily.add(new DaySales(d, list.size(), sum(list, Order::getTotalAmount)));
        }
        BigDecimal gross = sum(valid, Order::getTotalAmount);
        return new SalesReport(start, end, valid.size(), count(orders, OrderStatus.CANCELLED), gross,
                sum(valid, Order::getTaxAmount),
                sum(valid, Order::getCouponDiscount).add(sum(valid, Order::getLoyaltyDiscount)),
                avg(gross, valid.size()), daily, popular(r.getId(), f, t, 10));
    }

    public FeedbackReport feedback() {
        Restaurant r = currentUser.merchantRestaurant();
        List<Feedback> all = feedbackRepository.findByRestaurantIdOrderByCreatedAtDesc(r.getId());
        List<Feedback> reviews = all.stream().filter(f -> f.getType() == FeedbackType.REVIEW && f.getRating() != null).toList();
        Map<Integer, Long> dist = new TreeMap<>();
        for (int i = 1; i <= 5; i++) dist.put(i, 0L);
        reviews.forEach(f -> dist.merge(f.getRating(), 1L, Long::sum));
        Double avgRating = reviews.isEmpty() ? null : BigDecimal.valueOf(
                reviews.stream().mapToInt(Feedback::getRating).average().orElse(0)).setScale(1, RoundingMode.HALF_UP).doubleValue();
        List<Feedback> complaints = all.stream().filter(f -> f.getType() == FeedbackType.COMPLAINT).toList();
        return new FeedbackReport(reviews.size(), avgRating, dist, complaints.size(),
                complaints.stream().filter(f -> f.getStatus() != FeedbackStatus.RESOLVED).count(),
                complaints.stream().filter(f -> f.getStatus() == FeedbackStatus.RESOLVED).count());
    }

    /** Customer management: customers who ordered from the merchant's restaurant. */
    public List<CustomerSummaryDto> customers() {
        Restaurant r = currentUser.merchantRestaurant();
        List<CustomerSummaryDto> result = new ArrayList<>();
        for (Long id : orderRepository.findCustomerIdsByRestaurant(r.getId())) {
            userRepository.findById(id).ifPresent(u -> {
                List<Order> orders = orderRepository.findByRestaurantIdAndCustomerIdOrderByCreatedAtDesc(r.getId(), id);
                List<Order> valid = orders.stream().filter(o -> o.getStatus() != OrderStatus.CANCELLED).toList();
                result.add(new CustomerSummaryDto(u.getId(), u.getName(), u.getEmail(), u.getMobile(),
                        u.getLoyaltyPoints(), valid.size(), sum(valid, Order::getTotalAmount),
                        orders.isEmpty() ? null : orders.get(0).getCreatedAt()));
            });
        }
        result.sort(Comparator.comparing(CustomerSummaryDto::totalSpent).reversed());
        return result;
    }

    private List<PopularItem> popular(Long restaurantId, LocalDateTime from, LocalDateTime to, int limit) {
        return orderRepository.findPopularItems(restaurantId, from, to).stream().limit(limit)
                .map(v -> new PopularItem(v.getName(), v.getQuantity(), v.getRevenue().setScale(2, RoundingMode.HALF_UP)))
                .toList();
    }

    private static long count(List<Order> orders, OrderStatus s) {
        return orders.stream().filter(o -> o.getStatus() == s).count();
    }

    private static BigDecimal sum(List<Order> orders, java.util.function.Function<Order, BigDecimal> fn) {
        return orders.stream().map(fn).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal avg(BigDecimal total, int n) {
        return n == 0 ? BigDecimal.ZERO.setScale(2) : total.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
    }
}
