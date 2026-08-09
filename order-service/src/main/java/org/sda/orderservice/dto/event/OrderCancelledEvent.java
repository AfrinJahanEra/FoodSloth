package org.sda.orderservice.dto.event;

/**
 * Published as {@code order.cancelled}. Raised when the customer cancels, when the kitchen rejects
 * a paid order, or when a checkout could not be priced at all - {@code reason} says which.
 *
 * <p>Restaurant Service stops cooking, Delivery Service stops any live job, Payment Service refunds
 * a card charge, and Notification Service tells the customer.
 */
public record OrderCancelledEvent(
        String orderId,
        Long orderNo,
        String userId,
        String restaurantId,
        String reason
) {
}
