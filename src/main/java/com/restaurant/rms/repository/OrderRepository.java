package com.restaurant.rms.repository;

import com.restaurant.rms.model.Order;
import com.restaurant.rms.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    List<Order> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId);
    List<Order> findByRestaurantIdAndStatusOrderByCreatedAtDesc(Long restaurantId, OrderStatus status);
    List<Order> findByRestaurantIdAndStatusInOrderByCreatedAtAsc(Long restaurantId, Collection<OrderStatus> statuses);
    List<Order> findByRestaurantIdAndCreatedAtBetween(Long restaurantId, LocalDateTime from, LocalDateTime to);
    List<Order> findByRestaurantIdAndCustomerIdOrderByCreatedAtDesc(Long restaurantId, Long customerId);

    long countByCustomerIdAndCouponCodeIgnoreCaseAndRestaurantIdAndStatusNot(
            Long customerId, String couponCode, Long restaurantId, OrderStatus status);

    @Query("""
            select oi.itemName as name, sum(oi.quantity) as quantity, sum(oi.lineTotal) as revenue
            from OrderItem oi
            where oi.order.restaurant.id = :restaurantId
              and oi.order.status <> com.restaurant.rms.model.OrderStatus.CANCELLED
              and oi.order.createdAt between :from and :to
            group by oi.itemName
            order by sum(oi.quantity) desc
            """)
    List<PopularItemView> findPopularItems(@Param("restaurantId") Long restaurantId,
                                           @Param("from") LocalDateTime from,
                                           @Param("to") LocalDateTime to);

    @Query("""
            select distinct o.customer.id from Order o where o.restaurant.id = :restaurantId
            """)
    List<Long> findCustomerIdsByRestaurant(@Param("restaurantId") Long restaurantId);

    interface PopularItemView {
        String getName();
        Long getQuantity();
        java.math.BigDecimal getRevenue();
    }
}
