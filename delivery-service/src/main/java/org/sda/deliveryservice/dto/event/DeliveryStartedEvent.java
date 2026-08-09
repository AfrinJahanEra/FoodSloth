package org.sda.deliveryservice.dto.event;

/** Published on {@code delivery.started} when the rider leaves the restaurant with the food. */
public record DeliveryStartedEvent(
        String orderId,
        String userId,
        String deliveryId,
        String riderId,
        Integer etaMinutes
) {
}
