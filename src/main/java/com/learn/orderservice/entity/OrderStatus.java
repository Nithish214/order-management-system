package com.learn.orderservice.entity;

public enum OrderStatus {
    // Order row written; Order Service has not yet heard back from Inventory Service.
    PENDING,
    // Inventory Service reserved stock for every item (consumed from inventory.reserved).
    CONFIRMED,
    // Inventory Service could not reserve stock for at least one item (consumed from inventory.failed).
    CANCELLED,
    // Reserved for outcomes other than a stock shortage (e.g. malformed event) -- unused for now.
    FAILED
}
