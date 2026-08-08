package org.sda.notificationservice.dto.event;

// Mirrors the payload published by Order Service on order.cancelled.
public record OrderCancelledEvent(
        String orderId,
        String userId,
        String restaurantId
) {
}
