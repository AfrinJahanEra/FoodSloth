package org.sda.notificationservice.config;

public final class Constants {

    private Constants() {
    }

    // Exchange owned by Order Service; Notification Service only binds queues to it.
    public static final String ORDER_EXCHANGE = "order.exchange";

    public static final String ROUTING_KEY_ORDER_CONFIRMED = "order.confirmed";
    public static final String ROUTING_KEY_ORDER_CANCELLED = "order.cancelled";
    public static final String ROUTING_KEY_ORDER_DELIVERED = "order.delivered";

    public static final String QUEUE_ORDER_CONFIRMED = "notification.order-confirmed.queue";
    public static final String QUEUE_ORDER_CANCELLED = "notification.order-cancelled.queue";
    public static final String QUEUE_ORDER_DELIVERED = "notification.order-delivered.queue";
}
