package org.sda.deliveryservice.dto;

/** Body of the admin-only {@code POST /deliveries/{deliveryId}/assign}. */
public record AssignRiderRequest(String riderId) {
}
