package org.sda.deliveryservice.service;

import org.sda.deliveryservice.entity.GeoPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Distance and ETA maths.
 *
 * <p>Distances are straight-line (haversine) rather than road-network, and speed is a single
 * configured city average. That is deliberately simple: it needs no external routing provider,
 * so it keeps Delivery Service self-contained. Swapping in a routing API later only means
 * replacing this one class.
 */
@Component
public class RouteEstimator {

    private static final double EARTH_RADIUS_KM = 6371.0;

    @Value("${delivery.average-speed-kmph}")
    private double averageSpeedKmph;

    @Value("${delivery.handover-minutes}")
    private int handoverMinutes;

    /** Great-circle distance between two points, in kilometres. */
    public double distanceKm(GeoPoint from, GeoPoint to) {
        double lat1 = Math.toRadians(from.getLatitude());
        double lat2 = Math.toRadians(to.getLatitude());
        double deltaLat = lat2 - lat1;
        double deltaLon = Math.toRadians(to.getLongitude() - from.getLongitude());

        double a = Math.pow(Math.sin(deltaLat / 2), 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(deltaLon / 2), 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** Riding minutes for a distance, rounded up, plus the flat hand-over allowance. */
    public int etaMinutes(double distanceKm) {
        double ridingMinutes = (distanceKm / averageSpeedKmph) * 60;
        return (int) Math.ceil(ridingMinutes) + handoverMinutes;
    }

    public Instant etaAt(int etaMinutes) {
        return Instant.now().plusSeconds(etaMinutes * 60L);
    }
}
