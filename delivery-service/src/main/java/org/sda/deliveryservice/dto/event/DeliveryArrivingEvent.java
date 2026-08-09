package org.sda.deliveryservice.dto.event;

/**
 * Published once per delivery on {@code delivery.arriving}, the first time the rider's GPS ping
 * lands inside {@code delivery.arrival-radius-km} of the drop point.
 */
public record DeliveryArrivingEvent(
        String orderId,
        String userId,
        String deliveryId,
        String riderId,
        String riderDisplayName,
        Double remainingDistanceKm,
        Integer etaMinutes
) {
}
