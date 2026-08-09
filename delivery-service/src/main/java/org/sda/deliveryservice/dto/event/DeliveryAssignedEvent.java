package org.sda.deliveryservice.dto.event;

/** Published on {@code delivery.assigned} once a rider has been picked automatically. */
public record DeliveryAssignedEvent(
        String orderId,
        /** Sequential human-facing order number (#123) for the assignment message. */
        Long orderNo,
        String userId,
        String deliveryId,
        String riderId,
        String riderDisplayName,
        String riderPhone,
        Integer etaMinutes
) {
}
