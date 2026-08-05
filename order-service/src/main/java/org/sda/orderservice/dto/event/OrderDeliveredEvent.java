package org.sda.orderservice.dto.event;

// Published by Order Service once DeliveryCompleted is received.
public record OrderDeliveredEvent(
        String orderId,
        String userId,
        String restaurantId
) {
}
