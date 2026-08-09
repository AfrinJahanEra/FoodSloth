package org.sda.restaurantservice.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Consumed from {@code cart.checked-out} and {@code order.reorder-requested}.
 *
 * <p>The cart deliberately sends only {@code itemId} and {@code quantity}: names and prices belong
 * to this service, so the cart never has to look them up and can never hold a stale price. That is
 * what this service does next - it prices the line items against today's menu.
 *
 * <p>{@code orderId} was minted by Cart Service at checkout and is the correlation key for every
 * later event about this order.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CheckoutItem(String itemId, Integer quantity) {
    }
}
