package org.sda.orderservice.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Consumed from {@code restaurant.order-accepted} and {@code restaurant.order-rejected}.
 *
 * <p>One record for both because the kitchen's answer is the same shape either way; {@code reason}
 * is only filled in on a rejection. The routing key already says which decision it was.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KitchenDecisionEvent(
        String orderId,
        String userId,
        String restaurantId,
        String reason
) {
}
