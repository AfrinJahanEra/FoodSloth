package org.sda.orderservice.dto.event;

// Published by Order Service after an order is cancelled.
public record OrderCancelledEvent(
        String orderId,
        String userId,
        String restaurantId
) {
}
