package org.sda.orderservice.dto.event;

/**
 * Published as {@code order.confirmed} once the money is secured. Restaurant Service moves the
 * ticket onto the kitchen screen and Notification Service sends the confirmation email.
 */
public record OrderConfirmedEvent(
        String orderId,
        Long orderNo,
        String userId,
        String restaurantId,
        Double grandTotal
) {
}
