package org.sda.deliveryservice.dto.event;

/**
 * Consumed from {@code restaurant.order-ready}.
 *
 * <p>Carries every coordinate the job needs, which is exactly why Delivery Service never has to
 * call Restaurant Service or User Service. The drop coordinates were snapshotted from the address
 * the customer picked at checkout, so a later edit to that address cannot move a live delivery.
 */
public record OrderReadyEvent(
        String orderId,
        /** Sequential human-facing order number (#123) minted by Order Service; stored on the job. */
        Long orderNo,
        String userId,
        String restaurantId,
        Double pickupLatitude,
        Double pickupLongitude,
        Double dropLatitude,
        Double dropLongitude,
        String dropAddressLabel,
        String customerPhone
) {
}
