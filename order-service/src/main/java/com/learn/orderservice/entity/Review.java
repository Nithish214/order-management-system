package com.learn.orderservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// One row per (product, user) -- enforced by uq_review_product_user at the DB level, not
// just application logic, so a race between two near-simultaneous POSTs from the same
// buyer can't both succeed (the loser gets a real constraint violation, which
// ReviewController turns into a 409, not a second silent row).
@Entity
@Table(name = "review")
@Getter
@Setter
@NoArgsConstructor
@SequenceGenerator(name = "review_seq", sequenceName = "review_seq", allocationSize = 1)
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "review_seq")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false)
    private Integer rating;

    // Nullable -- a star rating alone is a complete, valid review; nothing about buying
    // something obligates a buyer to also write a paragraph about it.
    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Review(Product product, AppUser user, Integer rating, String comment) {
        this.product = product;
        this.user = user;
        this.rating = rating;
        this.comment = comment;
    }
}
