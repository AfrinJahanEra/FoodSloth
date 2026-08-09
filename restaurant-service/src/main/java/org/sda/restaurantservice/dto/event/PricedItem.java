package org.sda.restaurantservice.dto.event;

/**
 * One priced line of an order. Travels inside {@link OrderPricedEvent} and is stored on the kitchen
 * ticket, so the kitchen and the order agree on exactly what was ordered and at what price.
 */
public record PricedItem(
        String itemId,
        String name,
        double unitPrice,
        int quantity,
        double lineTotal
) {
}
