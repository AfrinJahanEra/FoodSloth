package org.sda.orderservice.entity;

public enum OrderStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    PREPARING,
    OUT_FOR_DELIVERY,
    DELIVERED,
    PAYMENT_FAILED,
    // TODO: reserved for a future "OrderRejected" event from Restaurant Service; no listener wired yet.
    REJECTED,
    CANCELLED
}
