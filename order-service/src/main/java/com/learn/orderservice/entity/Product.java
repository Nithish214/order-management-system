package com.learn.orderservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    // Null until an admin uploads one. Points at the final CloudFront URL, not the S3 key --
    // the actual bytes live in S3 (bucket: the same one hosting the frontend, under
    // product-images/), this column just remembers where.
    @Column(name = "image_url", length = 500)
    private String imageUrl;
}
