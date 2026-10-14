package com.restaurant.rms.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class ReportDtos {

    private ReportDtos() {}

    public record PopularItem(String name, long quantity, BigDecimal revenue) {}

    public record DailyReport(LocalDate date, long totalOrders, long completedOrders, long cancelledOrders,
                              long activeOrders, BigDecimal grossSales, BigDecimal amountCollected,
                              BigDecimal pendingAmount, BigDecimal totalTax, BigDecimal totalDiscounts,
                              BigDecimal averageOrderValue, Map<String, BigDecimal> paymentMethodBreakdown,
                              Map<Integer, Long> ordersByHour, List<PopularItem> popularItems) {}

    public record DaySales(LocalDate date, long orders, BigDecimal sales) {}

    public record SalesReport(LocalDate from, LocalDate to, long totalOrders, long cancelledOrders,
                              BigDecimal grossSales, BigDecimal totalTax, BigDecimal totalDiscounts,
                              BigDecimal averageOrderValue, List<DaySales> daily, List<PopularItem> popularItems) {}

    public record FeedbackReport(long totalReviews, Double averageRating, Map<Integer, Long> ratingDistribution,
                                 long totalComplaints, long openComplaints, long resolvedComplaints) {}
}
