package org.sda.notificationservice.messaging;

/**
 * RabbitMQ names used by Notification Service.
 *
 * <p>The whole platform shares one durable topic exchange, {@code food.exchange}. Routing keys are
 * {@code <owning-service>.<event>} so the publisher of any message is obvious from its key. Queues
 * are {@code <consuming-service>.<event>.queue} and are declared only by the service that consumes
 * them - Notification Service never declares another service's queue.
 *
 * <p>Notification Service is the platform's biggest listener and a pure consumer: it publishes
 * nothing of its own.
 *
 * <p>See README.md for the full platform-wide event catalogue.
 */
public final class Constants {

    private Constants() {
    }

    public static final String EXCHANGE = "food.exchange";

    // ---- Consumed: user accounts ----

    /** Announces every new account; replayed for each admin at User Service startup. */
    public static final String RK_USER_REGISTERED = "user.registered";

    public static final String QUEUE_USER_REGISTERED = "notification.user-registered.queue";

    // ---- Consumed: order lifecycle ----

    public static final String RK_ORDER_CONFIRMED = "order.confirmed";
    public static final String RK_ORDER_CANCELLED = "order.cancelled";
    public static final String RK_ORDER_DELIVERED = "order.delivered";

    public static final String QUEUE_ORDER_CONFIRMED = "notification.order-confirmed.queue";
    public static final String QUEUE_ORDER_CANCELLED = "notification.order-cancelled.queue";
    public static final String QUEUE_ORDER_DELIVERED = "notification.order-delivered.queue";

    // ---- Consumed: payment ----

    public static final String RK_PAYMENT_SUCCEEDED = "payment.succeeded";
    public static final String RK_PAYMENT_FAILED = "payment.failed";

    public static final String QUEUE_PAYMENT_SUCCEEDED = "notification.payment-succeeded.queue";
    public static final String QUEUE_PAYMENT_FAILED = "notification.payment-failed.queue";

    // ---- Consumed: kitchen ----

    public static final String RK_ORDER_ACCEPTED = "restaurant.order-accepted";
    public static final String RK_ORDER_REJECTED = "restaurant.order-rejected";
    public static final String RK_ORDER_READY = "restaurant.order-ready";

    public static final String QUEUE_ORDER_ACCEPTED = "notification.order-accepted.queue";
    public static final String QUEUE_ORDER_REJECTED = "notification.order-rejected.queue";
    public static final String QUEUE_ORDER_READY = "notification.order-ready.queue";

    // ---- Consumed: delivery ----

    public static final String RK_DELIVERY_ASSIGNED = "delivery.assigned";
    public static final String RK_DELIVERY_STARTED = "delivery.started";
    public static final String RK_DELIVERY_ARRIVING = "delivery.arriving";

    public static final String QUEUE_DELIVERY_ASSIGNED = "notification.delivery-assigned.queue";
    public static final String QUEUE_DELIVERY_STARTED = "notification.delivery-started.queue";
    public static final String QUEUE_DELIVERY_ARRIVING = "notification.delivery-arriving.queue";
}
