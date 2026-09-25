package com.learn.orderservice.service;

import com.learn.orderservice.dto.AnalyticsSummaryResponse;
import com.learn.orderservice.dto.AnalyticsSummaryResponse.DailyPoint;
import com.learn.orderservice.dto.AnalyticsSummaryResponse.StatusCount;
import com.learn.orderservice.dto.AnalyticsSummaryResponse.TopProduct;
import com.learn.orderservice.dto.AnalyticsSummaryResponse.TopRated;
import com.learn.orderservice.dto.AnalyticsSummaryResponse.Totals;
import com.learn.orderservice.repository.AnalyticsRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalyticsService {

    static final int WINDOW_DAYS = 30;
    private static final int TOP_N = 5;

    // The dashboard is a "how are we doing" view, not a live feed -- 60s-old numbers are
    // indistinguishable from fresh ones for that purpose, and this is what keeps a tab left
    // open (or several admins refreshing) from re-running four aggregate scans per view on a
    // box that's already the tightest resource in this deployment. Deliberately an
    // in-memory cache local to this JVM, not Redis: order-service has no Redis dependency
    // today, and one small object with a one-minute life isn't worth adding one for. The
    // worst case for a restart or a second instance is one extra computation.
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    private final AnalyticsRepository analyticsRepository;

    private record Cached(AnalyticsSummaryResponse summary, Instant computedAt) {}

    private volatile Cached cached;

    public AnalyticsService(AnalyticsRepository analyticsRepository) {
        this.analyticsRepository = analyticsRepository;
    }

    public AnalyticsSummaryResponse getSummary() {
        Cached current = cached;
        if (isFresh(current)) {
            return current.summary();
        }
        // Synchronized recompute with a re-check inside: if several requests all find the
        // cache expired at the same moment, exactly one does the work and the rest reuse
        // its result instead of each firing the same four queries.
        synchronized (this) {
            current = cached;
            if (isFresh(current)) {
                return current.summary();
            }
            AnalyticsSummaryResponse fresh = compute();
            cached = new Cached(fresh, Instant.now());
            return fresh;
        }
    }

    private boolean isFresh(Cached entry) {
        return entry != null && Duration.between(entry.computedAt(), Instant.now()).compareTo(CACHE_TTL) < 0;
    }

    private AnalyticsSummaryResponse compute() {
        LocalDate today = LocalDate.now();
        LocalDate firstDay = today.minusDays(WINDOW_DAYS - 1L);
        LocalDateTime since = firstDay.atStartOfDay();

        List<DailyPoint> daily = buildDaily(analyticsRepository.dailyTotalsSince(since), firstDay, today);

        List<StatusCount> statuses = new ArrayList<>();
        long ordersPlaced = 0;
        long ordersConfirmed = 0;
        for (Object[] row : analyticsRepository.statusCountsSince(since)) {
            String status = String.valueOf(row[0]);
            long count = toLong(row[1]);
            statuses.add(new StatusCount(status, count));
            ordersPlaced += count;
            if ("CONFIRMED".equals(status)) {
                ordersConfirmed = count;
            }
        }
        statuses.sort(Comparator.comparingLong(StatusCount::count).reversed());

        BigDecimal revenue = daily.stream().map(DailyPoint::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal averageOrderValue = ordersConfirmed == 0
                ? BigDecimal.ZERO
                : revenue.divide(BigDecimal.valueOf(ordersConfirmed), 2, RoundingMode.HALF_UP);

        List<TopProduct> topProducts = analyticsRepository.topProductsSince(since, TOP_N).stream()
                .map(row -> new TopProduct(toLong(row[0]), String.valueOf(row[1]), toLong(row[2]), toMoney(row[3])))
                .toList();

        List<TopRated> topRated = analyticsRepository.topRatedProducts(TOP_N).stream()
                .map(row -> new TopRated(toLong(row[0]), String.valueOf(row[1]), round2(((Number) row[2]).doubleValue()), toLong(row[3])))
                .toList();

        Object[] reviewTotals = analyticsRepository.reviewTotals();
        Double averageRating = reviewTotals[0] == null ? null : round2(((Number) reviewTotals[0]).doubleValue());
        long reviewCount = toLong(reviewTotals[1]);

        return new AnalyticsSummaryResponse(
                WINDOW_DAYS,
                Instant.now(),
                new Totals(revenue.setScale(2, RoundingMode.HALF_UP), ordersPlaced, ordersConfirmed,
                        averageOrderValue, averageRating, reviewCount),
                daily,
                statuses,
                topProducts,
                topRated
        );
    }

    // The database only returns days that had at least one order; a chart needs every day
    // in the window, with an explicit zero for the quiet ones -- otherwise a gap in sales
    // would silently collapse the x-axis and read as continuous trading.
    private List<DailyPoint> buildDaily(List<Object[]> rows, LocalDate firstDay, LocalDate lastDay) {
        Map<LocalDate, Object[]> byDate = new HashMap<>();
        for (Object[] row : rows) {
            byDate.put(toLocalDate(row[0]), row);
        }

        List<DailyPoint> days = new ArrayList<>();
        for (LocalDate day = firstDay; !day.isAfter(lastDay); day = day.plusDays(1)) {
            Object[] row = byDate.get(day);
            days.add(row == null
                    ? new DailyPoint(day, 0, BigDecimal.ZERO.setScale(2))
                    : new DailyPoint(day, toLong(row[1]), toMoney(row[2])));
        }
        return days;
    }

    private static long toLong(Object value) {
        return ((Number) value).longValue();
    }

    private static BigDecimal toMoney(Object value) {
        BigDecimal amount = value instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(value));
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private static double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    // Depending on the driver/dialect path, a SQL DATE arrives as java.sql.Date, LocalDate,
    // or (rarely) a java.util.Date subclass -- normalize all of them rather than betting on one.
    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate d) return d;
        if (value instanceof java.sql.Date d) return d.toLocalDate();
        if (value instanceof java.util.Date d) return new java.sql.Date(d.getTime()).toLocalDate();
        return LocalDate.parse(String.valueOf(value));
    }
}
