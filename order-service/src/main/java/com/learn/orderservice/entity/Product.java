package com.learn.orderservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "product")
@Getter
@Setter
@NoArgsConstructor
@SequenceGenerator(name = "product_seq", sequenceName = "product_seq", allocationSize = 1)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_seq")
    private Long id;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(name = "unit_price", nullable = false)
    private java.math.BigDecimal unitPrice;

    // A plain String, deliberately not a Java enum -- OrderStatus/OutboxStatus/
    // PaymentStatus elsewhere in this codebase ARE enums, and that's normally the more
    // consistent choice for "a fixed set of labeled values," but several actual category
    // names here ("Home & Kitchen", "Toys & Games") contain spaces and "&", which Java
    // enum constants can't represent directly -- forcing either an ugly mismatched
    // constant name (HOME_AND_KITCHEN storing as "Home & Kitchen") or a custom
    // AttributeConverter just to bridge the two. Not worth the complexity for a value
    // that's purely a display/filter label, never branched on in actual business logic
    // the way an order's status is.
    @Column(nullable = false, length = 50)
    private String category;

    // Empty until an admin uploads one -- a product can have several (see ProductImage),
    // ordered oldest-first (the same order they were uploaded in), which is what makes the
    // first entry this product's cover/thumbnail image everywhere else in the app. cascade
    // ALL + orphanRemoval: deleting an image from this list (see ProductController's DELETE
    // endpoint) removes its row too, the same relationship Order already has with its items.
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ProductImage> images = new ArrayList<>();
}
