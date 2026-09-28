package com.learn.orderservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "wishlist_item")
@Getter
@Setter
@NoArgsConstructor
@SequenceGenerator(name = "wishlist_item_seq", sequenceName = "wishlist_item_seq", allocationSize = 1)
public class WishlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "wishlist_item_seq")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    // Only the reference is stored -- price/name/image are always read live from Product at
    // request time (see WishlistItemResponse#from), same reasoning as CartItem#product.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public WishlistItem(AppUser user, Product product) {
        this.user = user;
        this.product = product;
    }
}
