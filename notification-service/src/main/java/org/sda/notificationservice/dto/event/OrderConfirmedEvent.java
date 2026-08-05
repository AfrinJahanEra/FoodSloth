package org.sda.notificationservice.dto.event;

// Mirrors the payload published by Order Service on order.confirmed.
public record OrderConfirmedEvent(
        String orderId,
        String restaurantId,
        String userId
) {
}
