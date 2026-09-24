package com.learn.orderservice.dto;

// Lets the frontend decide whether to even show a "write a review" form before the user
// tries, rather than rendering it unconditionally and only finding out it should have been
// hidden once POST /reviews comes back 403/409. The actual enforcement still happens
// server-side on that POST regardless -- same "shown vs. actually allowed" split as every
// other isAdmin-gated control in this app (see ProductDetailPage's own comment on that).
public record ReviewEligibilityResponse(boolean purchased, boolean alreadyReviewed) {
}
