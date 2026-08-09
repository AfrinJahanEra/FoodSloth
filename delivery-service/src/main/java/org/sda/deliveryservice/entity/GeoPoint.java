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

    /**
     * True for a believable GPS fix. Browsers and old clients can report the (0,0) area
     * (or the +-0.0001 sentinel) when they have no real position - that point sits in the
     * ocean and would poison every distance and ETA, so treat it as "no fix".
     */
    public boolean isReal() {
        return !(Math.abs(latitude) < 0.01 && Math.abs(longitude) < 0.01);
    }
}
