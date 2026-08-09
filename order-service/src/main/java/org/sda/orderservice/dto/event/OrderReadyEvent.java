package org.sda.orderservice.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Consumed from {@code restaurant.order-ready}: the kitchen packed the food and the order is
 * waiting for a rider. Only the correlation fields are needed here; the rest of the event
 * (coordinates etc.) is for Delivery Service and is ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderReadyEvent(
        String orderId,
        String userId,
        String restaurantId
) {
}
