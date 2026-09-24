package com.learn.orderservice.repository;

import com.learn.orderservice.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByProduct_IdOrderByCreatedAtDesc(Long productId);

    boolean existsByProduct_IdAndUser_Id(Long productId, Long userId);

    // A single aggregate query, not "load every Review row and average them in Java" --
    // the one place this matters at any real scale is a product with a lot of reviews,
    // where pulling every row just to compute two numbers would be pure waste.
    // AVG(rating) returns null (not 0/NaN) for a product with zero reviews -- see
    // RatingSummary's own comment on why that's handled as "no rating yet" rather than a
    // literal zero-star average.
    @Query("SELECT AVG(r.rating) as averageRating, COUNT(r) as reviewCount " +
            "FROM Review r WHERE r.product.id = :productId")
    RatingSummary getRatingSummary(@Param("productId") Long productId);

    interface RatingSummary {
        Double getAverageRating();
        Long getReviewCount();
    }
}
