package org.sda.orderservice.entity;

/**
 * The order lifecycle. Every transition is driven by an event; this service never moves a status
 * on its own.
 *
 * <pre>
 *   restaurant.order-priced -> PENDING_PAYMENT (+ order.payment-requested)
 *   payment.succeeded       -> CONFIRMED       (+ order.confirmed)
 *   payment.failed          -> PAYMENT_FAILED
 *   restaurant.order-accepted -> PREPARING
 *   restaurant.order-ready  -> READY
 *   restaurant.order-rejected -> REJECTED      (+ order.cancelled, so the charge is refunded)
 *   delivery.started        -> OUT_FOR_DELIVERY
 *   delivery.completed      -> DELIVERED       (+ order.delivered)
 *   customer cancels        -> CANCELLED       (+ order.cancelled)
 * </pre>
 */
public enum OrderStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    PREPARING,
    READY,
    OUT_FOR_DELIVERY,
    DELIVERED,
    PAYMENT_FAILED,
    REJECTED,
    CANCELLED
}
