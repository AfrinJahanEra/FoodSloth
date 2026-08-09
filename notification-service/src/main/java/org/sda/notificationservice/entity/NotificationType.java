package org.sda.notificationservice.entity;

/**
 * What happened, from the customer's point of view.
 *
 * <p>One value per event this service listens to. The type decides the wording and which channels
 * are used, which keeps that decision in one table instead of scattered across the listeners.
 */
public enum NotificationType {
    ORDER_CONFIRMED,
    ORDER_CANCELLED,
    ORDER_ACCEPTED,
    ORDER_REJECTED,
    ORDER_READY,
    ORDER_DELIVERED,
    PAYMENT_RECEIPT,
    PAYMENT_FAILED,
    RIDER_ASSIGNED,
    OUT_FOR_DELIVERY,
    RIDER_ARRIVING,
    /** Legacy: promotional campaigns were removed; kept so old stored notifications still load. */
    PROMOTION
}
