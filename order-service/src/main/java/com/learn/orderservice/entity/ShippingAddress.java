package com.learn.orderservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// One saved address in a user's address book. Mutable and deletable -- which is exactly why
// orders don't point at it (see ShippingAddressSnapshot): they copy it instead.
@Entity
@Table(name = "shipping_address")
@Getter
@Setter
@NoArgsConstructor
@SequenceGenerator(name = "shipping_address_seq", sequenceName = "shipping_address_seq", allocationSize = 1)
public class ShippingAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "shipping_address_seq")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    // Free text the user picks for their own benefit ("Home", "Work", "Mum's").
    @Column(nullable = false, length = 50)
    private String label;

    @Column(nullable = false, length = 200)
    private String line1;

    @Column(length = 200)
    private String line2;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false, length = 100)
    private String state;

    @Column(name = "postal_code", nullable = false, length = 20)
    private String postalCode;

    @Column(nullable = false, length = 100)
    private String country;

    // Named defaultAddress in Java (not isDefault): Lombok would turn a boolean field called
    // "isDefault" into isDefault(), which Jackson/JavaBeans then read as a property named
    // "default" -- a quiet way to end up with the wrong JSON key. The column is still
    // is_default. At most one true per user, guaranteed by the partial unique index in V25.
    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // What an order stores: the address's content, none of its identity or user-facing label.
    public ShippingAddressSnapshot toSnapshot() {
        return new ShippingAddressSnapshot(line1, line2, city, state, postalCode, country);
    }
}
