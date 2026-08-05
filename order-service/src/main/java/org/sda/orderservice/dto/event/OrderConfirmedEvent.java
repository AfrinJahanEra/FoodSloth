package org.sda.orderservice.dto.event;

// Published by Order Service after PaymentSucceeded is received.
public record OrderConfirmedEvent(
        String orderId,
        String restaurantId,
        String userId
) {
}
