package org.sda.restaurantservice.dto.event;

/**
 * Published as {@code restaurant.order-ready} - the food is packed and a rider is needed.
 *
 * <p>It carries both ends of the trip: the pickup point is this restaurant, and the drop point was
 * snapshotted from the address the customer chose at checkout. Delivery Service can therefore pick
 * the nearest rider and work out an ETA without calling anybody.
 */
public record OrderReadyEvent(
        String orderId,
        /** Sequential human-facing order number (#123) forwarded for the delivery record and messages. */
        Long orderNo,
        String userId,
        String restaurantId,
        Double pickupLatitude,
        Double pickupLongitude,
        Double dropLatitude,
        Double dropLongitude,
        String dropAddressLabel,
        /** The customer's phone number so the rider can call if the drop point is unclear. */
        String customerPhone
) {
}
