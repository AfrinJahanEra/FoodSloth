package org.sda.deliveryservice.service;

import org.sda.deliveryservice.entity.GeoPoint;
import org.sda.deliveryservice.entity.Rider;
import org.sda.deliveryservice.entity.RiderStatus;
import org.sda.deliveryservice.repository.RiderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Owns the Rider aggregate: shift state, last known position, and which rider is holding which job.
 *
 * <p>Deliberately knows nothing about deliveries beyond an id, so it can be used by
 * {@link DeliveryService} without the two depending on each other.
 */
@Service
public class RiderService {

    private final RiderRepository riderRepository;
    private final RouteEstimator routeEstimator;

    @Value("${delivery.assignment-radius-km}")
    private double assignmentRadiusKm;

    @Value("${delivery.location-max-age-minutes}")
    private long locationMaxAgeMinutes;

    public RiderService(RiderRepository riderRepository, RouteEstimator routeEstimator) {
        this.riderRepository = riderRepository;
        this.routeEstimator = routeEstimator;
    }

    /**
     * Starts a shift. The rider row is created on first use, keyed by the user id the gateway put
     * in {@code X-User-Id} - no call to User Service is needed to register a rider.
     */
    public Rider goOnline(String riderId, String displayName, String phone, String vehicleType,
                          double latitude, double longitude) {
        Rider rider = riderRepository.findById(riderId).orElseGet(() -> {
            Rider fresh = new Rider();
            fresh.setId(riderId);
            return fresh;
        });

        if (displayName != null && !displayName.isBlank()) {
            rider.setDisplayName(displayName);
        }
        if (phone != null && !phone.isBlank()) {
            rider.setPhone(phone);
        }
        if (vehicleType != null && !vehicleType.isBlank()) {
            rider.setVehicleType(vehicleType);
        }

        rider.setLocation(new GeoPoint(latitude, longitude));
        rider.setLocationUpdatedAt(Instant.now());
        // A rider who reconnects mid-job stays ON_DELIVERY; only a free rider becomes AVAILABLE.
        if (rider.getActiveDeliveryId() == null) {
            rider.setStatus(RiderStatus.AVAILABLE);
        }
        rider.setUpdatedAt(Instant.now());
        return riderRepository.save(rider);
    }

    /** Ends a shift. Refused while a job is still open so an order cannot be abandoned silently. */
    public Rider goOffline(String riderId) {
        Rider rider = require(riderId);
        if (rider.getActiveDeliveryId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Finish or decline delivery " + rider.getActiveDeliveryId() + " before going offline");
        }
        rider.setStatus(RiderStatus.OFFLINE);
        rider.setUpdatedAt(Instant.now());
        return riderRepository.save(rider);
    }

    /** Stores a GPS ping. */
    public Rider recordLocation(String riderId, double latitude, double longitude) {
        Rider rider = require(riderId);
        if (rider.getStatus() == RiderStatus.OFFLINE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Go online before sending location updates");
        }
        rider.setLocation(new GeoPoint(latitude, longitude));
        rider.setLocationUpdatedAt(Instant.now());
        rider.setUpdatedAt(Instant.now());
        return riderRepository.save(rider);
    }

    /**
     * Nearest rider to {@code pickup} that is free, reachable and inside the assignment radius.
     * {@code excludedRiderIds} carries the riders who already declined this job.
     */
    public Optional<Rider> findNearestAvailable(GeoPoint pickup, Collection<String> excludedRiderIds) {
        Instant freshnessCutoff = Instant.now().minus(Duration.ofMinutes(locationMaxAgeMinutes));

        return riderRepository.findByStatus(RiderStatus.AVAILABLE).stream()
                .filter(rider -> !excludedRiderIds.contains(rider.getId()))
                .filter(rider -> rider.getLocation() != null)
                // A rider whose app stopped reporting is treated as unreachable rather than free.
                .filter(rider -> rider.getLocationUpdatedAt() != null
                        && rider.getLocationUpdatedAt().isAfter(freshnessCutoff))
                .filter(rider -> routeEstimator.distanceKm(rider.getLocation(), pickup) <= assignmentRadiusKm)
                .min(Comparator.comparingDouble(rider -> routeEstimator.distanceKm(rider.getLocation(), pickup)));
    }

    /** Ties a rider to a delivery. */
    public Rider reserve(Rider rider, String deliveryId) {
        rider.setStatus(RiderStatus.ON_DELIVERY);
        rider.setActiveDeliveryId(deliveryId);
        rider.setUpdatedAt(Instant.now());
        return riderRepository.save(rider);
    }

    /** Frees a rider after a decline, a cancellation or a completed drop-off. */
    public void release(String riderId, boolean countCompletion) {
        if (riderId == null) {
            return;
        }
        riderRepository.findById(riderId).ifPresent(rider -> {
            rider.setActiveDeliveryId(null);
            // A rider who went offline mid-job must not be pulled back into the available pool.
            rider.setStatus(rider.getStatus() == RiderStatus.OFFLINE ? RiderStatus.OFFLINE : RiderStatus.AVAILABLE);
            if (countCompletion) {
                rider.setCompletedDeliveries(rider.getCompletedDeliveries() + 1);
            }
            rider.setUpdatedAt(Instant.now());
            riderRepository.save(rider);
        });
    }

    public Rider require(String riderId) {
        return riderRepository.findById(riderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Rider not registered; call POST /deliveries/riders/online first"));
    }

    public List<Rider> findAll() {
        return riderRepository.findAll();
    }
}
