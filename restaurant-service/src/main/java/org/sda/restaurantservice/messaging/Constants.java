package org.sda.restaurantservice.messaging;

/**
 * RabbitMQ names used by Restaurant Service.
 *
 * <p>The whole platform shares one durable topic exchange, {@code food.exchange}. Routing keys
 * are {@code <owning-service>.<event>} so the publisher of any message is obvious from its key.
 * Queues are {@code <consuming-service>.<event>.queue} and are declared only by the service that
 * consumes them - Restaurant Service never declares another service's queue.
 *
 * <p>See README.md for the full platform-wide event catalogue.
 */
public final class Constants {

    private Constants() {
    }

    public static final String EXCHANGE = "food.exchange";

    // ---- Consumed ----

    /** A customer checked their cart out; the order needs pricing. */
    public static final String RK_CART_CHECKED_OUT = "cart.checked-out";

    /** A customer re-ordered a past order; it needs re-pricing at today's prices. */
    public static final String RK_REORDER_REQUESTED = "order.reorder-requested";

    /** Payment cleared; the order may now enter the kitchen queue. */
    public static final String RK_ORDER_CONFIRMED = "order.confirmed";

    /** The order is off; stop cooking it. */
    public static final String RK_ORDER_CANCELLED = "order.cancelled";

    /**
     * Both intake keys land in one queue because they carry the same payload and are handled
     * identically - a fresh cart and a reorder are both just "price these items".
     */
    public static final String QUEUE_ORDER_INTAKE = "restaurant.order-intake.queue";
    public static final String QUEUE_ORDER_CONFIRMED = "restaurant.order-confirmed.queue";
    public static final String QUEUE_ORDER_CANCELLED = "restaurant.order-cancelled.queue";

    // ---- Published ----

    /** Items priced against the current menu; Order Service can now create the order. */
    public static final String RK_ORDER_PRICED = "restaurant.order-priced";

    /** One or more items are unknown or sold out; no order can be created. */
    public static final String RK_ORDER_UNAVAILABLE = "restaurant.order-unavailable";

    /** The kitchen accepted the order and started cooking. */
    public static final String RK_ORDER_ACCEPTED = "restaurant.order-accepted";

    /** The kitchen refused the order (too busy, closed, out of stock). */
    public static final String RK_ORDER_REJECTED = "restaurant.order-rejected";

    /** The food is packed and waiting for a rider. */
    public static final String RK_ORDER_READY = "restaurant.order-ready";
}
