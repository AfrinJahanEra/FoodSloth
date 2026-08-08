package org.sda.notificationservice.dto.event;

// Mirrors the payload published by Order Service on order.delivered.
public record OrderDeliveredEvent(
        String orderId,
        String userId,
        String restaurantId
) {
}
