package org.sda.restaurantservice.dto.event;

import java.util.List;

/**
 * Published as {@code restaurant.order-priced}. This is the message Order Service waits for before
 * it creates the order, so it carries everything an order needs and nothing has to be looked up.
 *
 * <p>The prices here are a snapshot taken at this moment. A later menu edit cannot change what the
 * customer was charged, which is why the amounts travel with the event instead of being re-read.
 */
public record OrderPricedEvent(
        String orderId,
        String userId,
        String restaurantId,
        String restaurantName,
        Double pickupLatitude,
        Double pickupLongitude,
        List<PricedItem> items,
        double itemsTotal,
        double deliveryFee,
        double tax,
        double grandTotal,
        String currency,
        String deliveryAddress,
        Double deliveryLatitude,
        Double deliveryLongitude,
        String paymentMethod,
        String note
) {
}
