package org.sda.deliveryservice.dto;

/** Body of {@code POST /deliveries/riders/online}. */
public record GoOnlineRequest(
        String displayName,
        String phone,
        String vehicleType,
        Double latitude,
        Double longitude
) {
}
