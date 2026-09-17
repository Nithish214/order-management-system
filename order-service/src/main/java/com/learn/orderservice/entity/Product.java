package com.learn.orderservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

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
    //
    // @BatchSize fixes a real N+1: this is a LAZY collection (the @OneToMany default),
    // and ProductResponse.from() reads it for every product in a list -- without this,
    // listing N products fires N+1 queries (one for the products, one MORE per product
    // for its images). With this, Hibernate instead loads images for up to 100 products
    // at once via a single "WHERE product_id IN (...)" query, however many products a
    // given list actually needs images for. 100 matches the page size GET /products now
    // uses (see ProductController) -- one page's worth of images in one extra query.
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @BatchSize(size = 100)
    private List<ProductImage> images = new ArrayList<>();
}
