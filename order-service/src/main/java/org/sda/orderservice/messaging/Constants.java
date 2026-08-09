package org.sda.orderservice.messaging;

/**
 * RabbitMQ names used by Order Service.
 *
 * <p>The whole platform shares one durable topic exchange, {@code food.exchange}. Routing keys
 * are {@code <owning-service>.<event>} so the publisher of any message is obvious from its key.
 * Queues are {@code <consuming-service>.<event>.queue} and are declared only by the service that
 * consumes them - Order Service never declares another service's queue.
 *
 * <p>See README.md for the full platform-wide event catalogue.
 */
public final class Constants {

    private Constants() {
    }

    public static final String EXCHANGE = "food.exchange";

    // ---- Consumed ----

    /** The checkout has been priced; this is what actually creates the order. */
    public static final String RK_ORDER_PRICED = "restaurant.order-priced";

    /** The checkout could not be priced; the order has to be closed with a reason. */
    public static final String RK_ORDER_UNAVAILABLE = "restaurant.order-unavailable";

    /** The kitchen took the order on. */
    public static final String RK_ORDER_ACCEPTED = "restaurant.order-accepted";

    /** The food is packed and waiting for a rider. */
    public static final String RK_ORDER_READY = "restaurant.order-ready";

    /** The kitchen turned the order down. */
    public static final String RK_ORDER_REJECTED = "restaurant.order-rejected";

    /** The card cleared, or cash on delivery was accepted. */
    public static final String RK_PAYMENT_SUCCEEDED = "payment.succeeded";

    /** The payment will not happen. */
    public static final String RK_PAYMENT_FAILED = "payment.failed";

    /** A rider picked the food up. */
    public static final String RK_DELIVERY_STARTED = "delivery.started";

    /** The food reached the customer. */
    public static final String RK_DELIVERY_COMPLETED = "delivery.completed";

    public static final String QUEUE_ORDER_PRICED = "order.restaurant-order-priced.queue";
    public static final String QUEUE_ORDER_UNAVAILABLE = "order.restaurant-order-unavailable.queue";
    public static final String QUEUE_ORDER_ACCEPTED = "order.restaurant-order-accepted.queue";
    public static final String QUEUE_ORDER_READY = "order.restaurant-order-ready.queue";
    public static final String QUEUE_ORDER_REJECTED = "order.restaurant-order-rejected.queue";
    public static final String QUEUE_PAYMENT_SUCCEEDED = "order.payment-succeeded.queue";
    public static final String QUEUE_PAYMENT_FAILED = "order.payment-failed.queue";
    public static final String QUEUE_DELIVERY_STARTED = "order.delivery-started.queue";
    public static final String QUEUE_DELIVERY_COMPLETED = "order.delivery-completed.queue";

    // ---- Published ----

    /** The order exists and needs paying for. */
    public static final String RK_PAYMENT_REQUESTED = "order.payment-requested";

    /** The money is in; the kitchen may cook. */
    public static final String RK_ORDER_CONFIRMED = "order.confirmed";

    /** The order is off - customer-cancelled, kitchen-rejected, or unpriceable. */
    public static final String RK_ORDER_CANCELLED = "order.cancelled";

    /** The customer has the food. */
    public static final String RK_ORDER_DELIVERED = "order.delivered";

    /** A past order asked for again; Restaurant Service re-prices it at today's prices. */
    public static final String RK_REORDER_REQUESTED = "order.reorder-requested";
}
