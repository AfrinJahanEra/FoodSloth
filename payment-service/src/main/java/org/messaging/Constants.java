package org.messaging;

/**
 * RabbitMQ names used by Payment Service.
 *
 * <p>The whole platform shares one durable topic exchange, {@code food.exchange}. Routing keys
 * are {@code <owning-service>.<event>} so the publisher of any message is obvious from its key.
 * Queues are {@code <consuming-service>.<event>.queue} and are declared only by the service that
 * consumes them - Payment Service never declares another service's queue.
 *
 * <p>See README.md for the full platform-wide event catalogue.
 */
public final class Constants {

    private Constants() {
    }

    public static final String EXCHANGE = "food.exchange";

    // ---- Consumed ----

    /** An order is waiting to be paid for. */
    public static final String RK_PAYMENT_REQUESTED = "order.payment-requested";

    /** An order was cancelled; refund it if it was already charged. */
    public static final String RK_ORDER_CANCELLED = "order.cancelled";

    /** A rider handed the order over; cash-on-delivery payments confirm at this moment. */
    public static final String RK_DELIVERY_COMPLETED = "delivery.completed";

    public static final String QUEUE_PAYMENT_REQUESTED = "payment.order-requested.queue";
    public static final String QUEUE_ORDER_CANCELLED = "payment.order-cancelled.queue";
    public static final String QUEUE_DELIVERY_COMPLETED = "payment.delivery-completed.queue";

    // ---- Published ----

    /** Money is secured (card captured, or cash-on-delivery accepted). */
    public static final String RK_PAYMENT_SUCCEEDED = "payment.succeeded";

    /** The payment will not happen (declined, expired, cancelled by the customer). */
    public static final String RK_PAYMENT_FAILED = "payment.failed";
}
