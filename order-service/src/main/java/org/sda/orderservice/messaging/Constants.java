package org.sda.orderservice.messaging;

public final class Constants {

    private Constants() {
    }

    // ---- Exchanges ----
    public static final String ORDER_EXCHANGE = "order.exchange";
    public static final String PAYMENT_EXCHANGE = "payment.exchange";
    public static final String RESTAURANT_EXCHANGE = "restaurant.exchange";
    public static final String DELIVERY_EXCHANGE = "delivery.exchange";

    // ---- Routing keys published by Order Service (on ORDER_EXCHANGE) ----
    public static final String ROUTING_KEY_PAYMENT_REQUESTED = "order.payment.requested";
    public static final String ROUTING_KEY_ORDER_CONFIRMED = "order.confirmed";
    public static final String ROUTING_KEY_ORDER_CANCELLED = "order.cancelled";
    public static final String ROUTING_KEY_ORDER_DELIVERED = "order.delivered";

    // ---- Routing keys consumed by Order Service ----
    public static final String ROUTING_KEY_PAYMENT_SUCCEEDED = "payment.succeeded";
    public static final String ROUTING_KEY_PAYMENT_FAILED = "payment.failed";
    public static final String ROUTING_KEY_ORDER_ACCEPTED = "restaurant.order.accepted";
    public static final String ROUTING_KEY_DELIVERY_STARTED = "delivery.started";
    public static final String ROUTING_KEY_DELIVERY_COMPLETED = "delivery.completed";

    // ---- Queues owned/consumed by Order Service ----
    public static final String QUEUE_PAYMENT_SUCCEEDED = "order.payment-succeeded.queue";
    public static final String QUEUE_PAYMENT_FAILED = "order.payment-failed.queue";
    public static final String QUEUE_ORDER_ACCEPTED = "order.restaurant-accepted.queue";
    public static final String QUEUE_DELIVERY_STARTED = "order.delivery-started.queue";
    public static final String QUEUE_DELIVERY_COMPLETED = "order.delivery-completed.queue";

    // TODO: Payment Service is not implemented yet. This queue is declared here so that
    // PaymentRequested events are not silently dropped before Payment Service exists to
    // declare/bind its own queue. Remove once Payment Service owns this queue itself.
    public static final String QUEUE_PAYMENT_REQUESTED = "payment.order-requested.queue";
}
