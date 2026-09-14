package com.learn.orderservice.entity;

public enum OrderStatus {
    // Order row written; Order Service has not yet heard back from Inventory Service.
    PENDING,
    // Inventory Service reserved stock for every item (consumed from inventory.reserved).
    CONFIRMED,
    // The customer cancelled this order themselves (see OrderController#cancelOrder). Only
    // reachable from PENDING or CONFIRMED -- an already-terminal order can't be cancelled.
    // Named CANCELLED (rather than the pre-existing enum value that used to have this name)
    // specifically so the word means what anyone calling this API would expect it to mean;
    // the old "Inventory couldn't fulfill this" outcome was renamed REJECTED instead of
    // being reused here, so the two meanings never collide under one name again.
    CANCELLED,
    // Inventory Service could not reserve stock for at least one item (consumed from
    // inventory.failed). Was previously named CANCELLED -- renamed so that name is free for
    // actual customer-initiated cancellation above. See migration V4 (Postgres) / V6
    // (Oracle) for the one-time data fix that relabels any pre-existing rows.
    REJECTED,
    // Reserved for outcomes other than a stock shortage (e.g. malformed event) -- unused for now.
    FAILED
}
