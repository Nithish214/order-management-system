package com.learn.inventoryservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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

    // Optimistic lock token (see V4__add_product_stock_version.sql). Hibernate reads this
    // alongside every other column, appends "AND version = ?" to its UPDATE using the value
    // it read, and sets the new value to old+1 -- all automatically, nothing in this class's
    // own code ever reads or writes it directly. If some other transaction already updated
    // (and so bumped) this row between our read and our write, the WHERE clause matches zero
    // rows and Hibernate throws OptimisticLockException instead of silently applying a write
    // based on data we now know was stale. No @Column needed: the field name "version"
    // already matches the migration's column name.
    @Version
    private Long version;
}
