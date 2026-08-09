package org.sda.restaurantservice.dto.event;

/**
 * Published as {@code restaurant.order-accepted} and {@code restaurant.order-rejected}.
 *
 * <p>One record for both because the kitchen's answer is the same shape either way; {@code reason}
 * is only filled in on a rejection. Order Service moves the order to PREPARING or REJECTED, and
 * Notification Service tells the customer.
 */
public record KitchenDecisionEvent(
        String orderId,
        String userId,
        String restaurantId,
        String reason
) {
}
