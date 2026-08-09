package org.sda.deliveryservice.dto;

import org.sda.deliveryservice.entity.GeoPoint;
import org.sda.deliveryservice.entity.Rider;
import org.sda.deliveryservice.entity.RiderStatus;

import java.time.Instant;

/**
 * A rider as the rider app and the admin overview see them. Creation timestamps are internal
 * bookkeeping and stay off the wire.
 */
public record RiderResponse(
        String id,
        String displayName,
        String phone,
        String vehicleType,
        RiderStatus status,
        GeoPoint location,
        Instant locationUpdatedAt,
        String activeDeliveryId,
        int completedDeliveries) {

    public static RiderResponse from(Rider rider) {
        return new RiderResponse(rider.getId(), rider.getDisplayName(), rider.getPhone(), rider.getVehicleType(),
                rider.getStatus(), rider.getLocation(), rider.getLocationUpdatedAt(), rider.getActiveDeliveryId(),
                rider.getCompletedDeliveries());
    }
}
