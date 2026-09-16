package com.learn.orderservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// One of possibly several photos for a product (see Product#images). Whichever row has the
// lowest id is simply whichever image was uploaded first -- that's this product's cover/
// thumbnail image everywhere else in the app shows one (the product list row, cart, etc.),
// with no separate isPrimary flag or display-order column needed to say so.
@Entity
@Table(name = "product_image")
@Getter
@Setter
@NoArgsConstructor
@SequenceGenerator(name = "product_image_seq", sequenceName = "product_image_seq", allocationSize = 1)
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_image_seq")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // Points at the final CloudFront URL, not the S3 key -- the actual bytes live in S3
    // (see ProductImageUploadService), this column just remembers where.
    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    public ProductImage(Product product, String imageUrl) {
        this.product = product;
        this.imageUrl = imageUrl;
    }
}
