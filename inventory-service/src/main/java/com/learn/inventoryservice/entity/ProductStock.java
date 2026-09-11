package com.learn.inventoryservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "product_stock")
@Getter
@Setter
@NoArgsConstructor
public class ProductStock {

    // No @GeneratedValue: this id intentionally mirrors Order Service's product id
    // (see the migration's seed data), not a value this service generates itself.
    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(nullable = false)
    private String sku;

    @Column(name = "available_quantity", nullable = false)
    private Integer availableQuantity;
}
