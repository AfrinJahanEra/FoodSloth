package org.sda.deliveryservice.dto.event;

/** Published on {@code delivery.started} when the rider leaves the restaurant with the food. */
public record DeliveryStartedEvent(
        String orderId,
        /** Sequential human-facing order number (#123) for the out-for-delivery message. */
        Long orderNo,
        String userId,
        String deliveryId,
        String riderId,
        Integer etaMinutes
) {
}
