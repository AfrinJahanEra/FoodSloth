package org.sda.notificationservice.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Consumed from {@code restaurant.order-rejected} - the kitchen turned the order down. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderRejectedEvent(
        String orderId,
        String userId,
        String restaurantId,
        String reason
) {
}
