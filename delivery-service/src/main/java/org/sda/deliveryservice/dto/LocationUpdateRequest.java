package org.sda.deliveryservice.dto;

/**
 * A live GPS push from the rider app - the browser streams its position while the
 * rider is online so the customer's tracking map can follow the ride in real time.
 */
public record LocationUpdateRequest(Double latitude, Double longitude) {
}
