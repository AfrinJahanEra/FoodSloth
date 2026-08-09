package org.sda.userservice.messaging;

/**
 * RabbitMQ names used by User Service.
 *
 * <p>The whole platform shares one durable topic exchange, {@code food.exchange}. Routing keys are
 * {@code <owning-service>.<event>} so the publisher of any message is obvious from its key. User
 * Service only publishes - it consumes nothing - so no queues are declared here.
 */
public final class Constants {

    private Constants() {
    }

    public static final String EXCHANGE = "food.exchange";

    /** Raised once per new account, and re-raised for every admin at startup. */
    public static final String RK_USER_REGISTERED = "user.registered";
}
