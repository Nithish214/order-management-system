package com.learn.paymentservice.entity;

public enum PaymentStatus {
    // Unused today -- the simulated payment in InventoryReservedListener decides
    // COMPLETED or FAILED synchronously, in the same transaction that creates the row, so
    // it never actually passes through PENDING first. Kept in the schema/enum for a real
    // payment gateway later, where a row would genuinely sit PENDING while waiting on an
    // async webhook.
    PENDING,
    COMPLETED,
    FAILED
}
