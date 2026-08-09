package org.sda.deliveryservice.dto;

import org.sda.deliveryservice.entity.Delivery;
import org.sda.deliveryservice.entity.DeliveryStatus;

import java.time.Instant;

/**
 * What the customer's tracking screen needs: where the rider is now, and when they will arrive.
 *
 * <p>Drawing the map is the client's job; this is the feed behind it. Polling this endpoint every
 * few seconds gives a live marker without any websocket infrastructure.
 */
public record TrackingResponse(
        String orderId,
        String deliveryId,
        DeliveryStatus status,
        String riderId,
        String riderName,
        String riderPhone,
        Double riderLatitude,
        Double riderLongitude,
        Instant riderLocationUpdatedAt,
        double pickupLatitude,
        double pickupLongitude,
        double dropLatitude,
        double dropLongitude,
        String dropAddressLabel,
        Double remainingDistanceKm,
        Integer etaMinutes,
        Instant etaAt
) {

    public static TrackingResponse from(Delivery delivery) {
        return new TrackingResponse(
                delivery.getOrderId(),
                delivery.getId(),
                delivery.getStatus(),
                delivery.getRiderId(),
                delivery.getRiderDisplayName(),
                delivery.getRiderPhone(),
                delivery.getRiderLocation() == null ? null : delivery.getRiderLocation().getLatitude(),
                delivery.getRiderLocation() == null ? null : delivery.getRiderLocation().getLongitude(),
                delivery.getRiderLocationUpdatedAt(),
                delivery.getPickup().getLatitude(),
                delivery.getPickup().getLongitude(),
                delivery.getDrop().getLatitude(),
                delivery.getDrop().getLongitude(),
                delivery.getDropAddressLabel(),
                delivery.getRemainingDistanceKm(),
                delivery.getEtaMinutes(),
                delivery.getEtaAt());
    }
}
