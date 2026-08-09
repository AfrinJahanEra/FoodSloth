package org.sda.deliveryservice.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A latitude/longitude pair. Used for the restaurant, the customer's door and the rider's
 * live position.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeoPoint {
    private double latitude;
    private double longitude;
}
