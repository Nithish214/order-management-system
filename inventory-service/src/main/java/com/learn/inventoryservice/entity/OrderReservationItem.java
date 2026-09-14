package com.learn.inventoryservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// One row per (order, product) this service actually reserved stock for -- written by
// OrderCreatedListener the moment it decrements available_quantity, and read back by
// OrderCancelledListener to know exactly how much to credit back. This is this service's
// own bookkeeping of what it did, not a repeat of what Order Service asked for -- if an
// order was REJECTED (never reserved at all), no rows exist here for it, so cancelling a
// rejected order correctly releases nothing instead of over-crediting stock that was never
// taken in the first place.
@Entity
@Table(name = "order_reservation_item")
@Getter
@Setter
@NoArgsConstructor
@SequenceGenerator(name = "order_reservation_item_seq", sequenceName = "order_reservation_item_seq", allocationSize = 1)
public class OrderReservationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_reservation_item_seq")
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    public OrderReservationItem(Long orderId, Long productId, Integer quantity) {
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
    }
}
