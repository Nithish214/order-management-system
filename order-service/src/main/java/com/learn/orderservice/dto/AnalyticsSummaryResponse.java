package com.learn.orderservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// Everything the admin dashboard renders, in one response -- one round trip and one cache
// entry (see AnalyticsService) rather than five endpoints that would each re-scan the same
// tables. Money is BigDecimal end to end, same as Order#totalAmount.
public record AnalyticsSummaryResponse(
        int windowDays,
        Instant generatedAt,
        Totals totals,
        List<DailyPoint> daily,
        List<StatusCount> statusBreakdown,
        List<TopProduct> topProducts,
        List<TopRated> topRated
) {

    // revenue/averageOrderValue only ever count CONFIRMED orders -- a PENDING, REJECTED, or
    // CANCELLED order never turned into money. ordersPlaced counts every order created in
    // the window regardless of outcome, so the gap between the two is itself informative.
    // averageRating is null (not 0) when there are no reviews at all -- "no data" and "rated
    // zero stars" are different things, and 0 isn't even a valid rating.
    public record Totals(
            BigDecimal revenue,
            long ordersPlaced,
            long ordersConfirmed,
            BigDecimal averageOrderValue,
            Double averageRating,
            long reviewCount
    ) {}

    public record DailyPoint(LocalDate date, long orders, BigDecimal revenue) {}

    public record StatusCount(String status, long count) {}

    public record TopProduct(Long productId, String name, long unitsSold, BigDecimal revenue) {}

    public record TopRated(Long productId, String name, double averageRating, long reviewCount) {}
}
