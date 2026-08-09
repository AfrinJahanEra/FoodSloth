package com.service.cart_service.messaging;

/**
 * RabbitMQ names used by Cart Service.
 *
 * <p>The whole platform shares one durable topic exchange, {@code food.exchange}. Routing keys
 * are {@code <owning-service>.<event>} so the publisher of any message is obvious from its key.
 * Queues are {@code <consuming-service>.<event>.queue} and are declared only by the service that
 * consumes them - Cart Service publishes {@code cart.checked-out} but declares none of the queues
 * bound to it, so it can never step on a consumer's declaration.
 *
 * <p>See README.md for the full platform-wide event catalogue.
 */
public final class Constants {

    private Constants() {
    }

    public static final String EXCHANGE = "food.exchange";

    /** A customer checked their cart out. Restaurant Service prices it and Order Service creates it. */
    public static final String RK_CART_CHECKED_OUT = "cart.checked-out";
}
