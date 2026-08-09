package com.service.cart_service.dto.event;

import java.util.List;

/**
 * Published as {@code cart.checked-out} at checkout. This is the event that starts the whole order
 * pipeline, so it carries everything the next services need and nothing they have to look up.
 *
 * <p>{@code orderId} is minted here, at checkout, as a UUID, and every later event about this
 * order carries the same id - it is the platform's correlation key. The client gets it back
 * immediately in the 202 response and then polls {@code GET /orders/{orderId}}.
 *
 * <p>Deliberately no prices: the cart only knows item ids and quantities, and Restaurant Service
 * prices them against the current menu before the order is even created.
 */
public record CartCheckedOutEvent(
        String orderId,
        String userId,
        List<CheckoutItem> items,
        String deliveryAddress,
        Double deliveryLatitude,
        Double deliveryLongitude,
        String paymentMethod,
        String note
) {

    public record CheckoutItem(String itemId, int quantity) {
    }
}
