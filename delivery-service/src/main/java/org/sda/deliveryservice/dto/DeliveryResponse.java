package org.sda.deliveryservice.dto;

import org.sda.deliveryservice.entity.Delivery;
import org.sda.deliveryservice.entity.DeliveryStatus;
import org.sda.deliveryservice.entity.GeoPoint;

import java.time.Instant;

/**
 * A delivery job as the rider app and the admin overview render it. The decline list and the
 * arrival-push guard are internal machinery and stay off the wire.
 */
public record DeliveryResponse(
        String id,
        String orderId,
        Long orderNo,
        String userId,
        String restaurantId,
        GeoPoint pickup,
        GeoPoint drop,
        String dropAddressLabel,
        String customerPhone,
        DeliveryStatus status,
        String riderId,
        String riderDisplayName,
        String riderPhone,
        GeoPoint riderLocation,
        Instant riderLocationUpdatedAt,
        Double remainingDistanceKm,
        Integer etaMinutes,
        Instant etaAt,
        Instant assignedAt,
        Instant acceptedAt,
        Instant pickedUpAt,
        Instant deliveredAt,
        Instant createdAt,
        Instant updatedAt) {

    public static DeliveryResponse from(Delivery delivery) {
        return new DeliveryResponse(delivery.getId(), delivery.getOrderId(), delivery.getOrderNo(), delivery.getUserId(),
                delivery.getRestaurantId(), delivery.getPickup(), delivery.getDrop(), delivery.getDropAddressLabel(),
                delivery.getCustomerPhone(),
                delivery.getStatus(), delivery.getRiderId(), delivery.getRiderDisplayName(), delivery.getRiderPhone(),
                delivery.getRiderLocation(), delivery.getRiderLocationUpdatedAt(), delivery.getRemainingDistanceKm(),
                delivery.getEtaMinutes(), delivery.getEtaAt(), delivery.getAssignedAt(), delivery.getAcceptedAt(),
                delivery.getPickedUpAt(), delivery.getDeliveredAt(), delivery.getCreatedAt(), delivery.getUpdatedAt());
    }
}
