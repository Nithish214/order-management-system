package com.learn.orderservice.dto;

import com.learn.orderservice.entity.Review;

import java.time.LocalDateTime;

// reviewerName, not a userId -- nothing on the product detail page needs to look this
// reviewer up further, and exposing a raw internal user id here for no reason is exactly
// the kind of thing sku's own redaction elsewhere in this codebase already treats as
// needless exposure.
public record ReviewResponse(Long id, String reviewerName, Integer rating, String comment, LocalDateTime createdAt) {

    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getUser().getName(),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt());
    }
}
