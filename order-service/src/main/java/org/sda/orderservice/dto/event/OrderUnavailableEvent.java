package org.sda.orderservice.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Consumed from {@code restaurant.order-unavailable} - the checkout could not be priced at all. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderUnavailableEvent(
        String orderId,
        String userId,
        String restaurantId,
        String reason
) {
}
