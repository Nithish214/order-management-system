package com.learn.orderservice.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// Plain native SQL through the EntityManager rather than Spring Data @Query methods: every
// query here is an aggregate over columns that don't map to any one entity, so there's no
// entity or interface projection to return. Rows come back as Object[] by position, which
// also sidesteps a real Postgres quirk with interface projections -- unquoted aliases are
// lowercased by the database, so a camelCase getter like getOrderCount() silently fails to
// match its own column.
//
// Read-only and aggregate-only: nothing here can write, and every result set is small (a
// row per day / status / top-N), no matter how many orders exist.
@Repository
@Transactional(readOnly = true)
public class AnalyticsRepository {

    @PersistenceContext
    private EntityManager entityManager;

    // [date, orders placed, revenue from CONFIRMED orders]
    public List<Object[]> dailyTotalsSince(LocalDateTime since) {
        return query("""
                select cast(o.created_at as date),
                       count(*),
                       coalesce(sum(case when o.status = 'CONFIRMED' then o.total_amount else 0 end), 0)
                from orders o
                where o.created_at >= :since
                group by cast(o.created_at as date)
                """, since);
    }

    // [status, count]
    public List<Object[]> statusCountsSince(LocalDateTime since) {
        return query("""
                select o.status, count(*)
                from orders o
                where o.created_at >= :since
                group by o.status
                """, since);
    }

    // [product id, name, units sold, revenue] -- CONFIRMED orders only, same "did it become
    // money" rule as the daily revenue above.
    public List<Object[]> topProductsSince(LocalDateTime since, int limit) {
        return query("""
                select p.id, p.name, sum(oi.quantity), sum(oi.line_total)
                from order_item oi
                join orders o on o.id = oi.order_id
                join product p on p.id = oi.product_id
                where o.status = 'CONFIRMED' and o.created_at >= :since
                group by p.id, p.name
                order by sum(oi.quantity) desc, sum(oi.line_total) desc, p.id
                limit %d
                """.formatted(limit), since);
    }

    // [product id, name, average rating, review count] -- all-time, not windowed: a review
    // is a lasting signal about a product, not something that expires after 30 days.
    public List<Object[]> topRatedProducts(int limit) {
        return query("""
                select p.id, p.name, avg(r.rating), count(*)
                from review r
                join product p on p.id = r.product_id
                group by p.id, p.name
                order by avg(r.rating) desc, count(*) desc, p.id
                limit %d
                """.formatted(limit), null);
    }

    // [average rating (null when there are no reviews), review count]
    public Object[] reviewTotals() {
        return query("select avg(rating), count(*) from review", null).get(0);
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> query(String sql, LocalDateTime since) {
        var query = entityManager.createNativeQuery(sql);
        if (since != null) {
            query.setParameter("since", since);
        }
        return query.getResultList();
    }
}
