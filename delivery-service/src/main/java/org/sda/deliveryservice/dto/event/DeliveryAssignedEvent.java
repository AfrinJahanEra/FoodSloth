package org.sda.deliveryservice.dto.event;

/** Published on {@code delivery.assigned} once a rider has been picked automatically. */
public record DeliveryAssignedEvent(
        String orderId,
        String userId,
        String deliveryId,
        String riderId,
        String riderDisplayName,
        String riderPhone,
        Integer etaMinutes
) {
}
