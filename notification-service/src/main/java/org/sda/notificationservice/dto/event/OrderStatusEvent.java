package org.sda.notificationservice.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Consumed from the order-status events that carry nothing beyond the order itself:
 * {@code order.delivered}, {@code restaurant.order-accepted} and {@code restaurant.order-ready}.
 *
 * <p>They share one record because Notification Service treats them identically - the routing key
 * already says which one it is, and the listener passes the matching type through.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderStatusEvent(
        String orderId,
        String userId,
        String restaurantId
) {
}
