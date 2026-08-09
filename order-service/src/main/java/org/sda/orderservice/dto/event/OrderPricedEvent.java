package org.sda.orderservice.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Consumed from {@code restaurant.order-priced} - the message that actually creates the order.
 *
 * <p>It carries the whole priced checkout, so the order is built from this snapshot alone and Order
 * Service never has to ask Restaurant Service for anything. Prices are frozen here: a later menu
 * edit cannot change what the customer was charged.
 *
 * <p>The document id will be this event's {@code orderId} - the UUID Cart Service minted at
 * checkout - so the client can poll {@code GET /orders/{orderId}} from the very first moment.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderPricedEvent(
        String orderId,
        String userId,
        String restaurantId,
        String restaurantName,
        Double pickupLatitude,
        Double pickupLongitude,
        List<PricedItem> items,
        Double itemsTotal,
        Double deliveryFee,
        Double tax,
        Double grandTotal,
        String currency,
        String deliveryAddress,
        Double deliveryLatitude,
        Double deliveryLongitude,
        String paymentMethod,
        String note
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PricedItem(
            String itemId,
            String name,
            Double unitPrice,
            Integer quantity,
            Double lineTotal
    ) {
    }
}
