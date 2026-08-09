package org.sda.restaurantservice.dto.event;

/**
 * Published as {@code restaurant.order-unavailable} when the checkout cannot be priced at all -
 * an unknown or sold-out item, an empty cart, or the restaurant being closed.
 *
 * <p>Order Service turns this into a rejected order the customer can see, so a checkout never
 * disappears silently just because it could not be fulfilled.
 */
public record OrderUnavailableEvent(
        String orderId,
        String userId,
        String restaurantId,
        String reason
) {
}
