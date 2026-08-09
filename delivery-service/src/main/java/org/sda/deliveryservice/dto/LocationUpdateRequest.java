package org.sda.deliveryservice.dto;

/** Body of {@code PUT /deliveries/riders/location}, sent repeatedly by the rider app. */
public record LocationUpdateRequest(
        Double latitude,
        Double longitude
) {
}
