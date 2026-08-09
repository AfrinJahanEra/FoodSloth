package org.sda.deliveryservice.messaging;

/**
 * RabbitMQ names used by Delivery Service.
 *
 * <p>The whole platform shares one durable topic exchange, {@code food.exchange}. Routing keys
 * are {@code <owning-service>.<event>} so the publisher of any message is obvious from its key.
 * Queues are {@code <consuming-service>.<event>.queue} and are declared only by the service that
 * consumes them - Delivery Service never declares another service's queue.
 *
 * <p>See README.md for the full platform-wide event catalogue.
 */
public final class Constants {

    private Constants() {
    }

    public static final String EXCHANGE = "food.exchange";

    // ---- Consumed ----

    /** Restaurant Service has finished cooking; the order needs a rider. */
    public static final String RK_ORDER_READY = "restaurant.order-ready";

    /** The customer (or the system) cancelled the order; release the rider. */
    public static final String RK_ORDER_CANCELLED = "order.cancelled";

    public static final String QUEUE_ORDER_READY = "delivery.order-ready.queue";
    public static final String QUEUE_ORDER_CANCELLED = "delivery.order-cancelled.queue";

    // ---- Published ----

    /** A rider has been auto-assigned to a delivery. */
    public static final String RK_DELIVERY_ASSIGNED = "delivery.assigned";

    /** The rider collected the food and is on the way to the customer. */
    public static final String RK_DELIVERY_STARTED = "delivery.started";

    /** The rider is within the arrival radius of the drop point. */
    public static final String RK_DELIVERY_ARRIVING = "delivery.arriving";

    /** The rider handed the order over to the customer. */
    public static final String RK_DELIVERY_COMPLETED = "delivery.completed";
}
