package org.sda.deliveryservice.service;

import org.sda.deliveryservice.dto.event.OrderReadyEvent;
import org.sda.deliveryservice.entity.Delivery;
import org.sda.deliveryservice.entity.DeliveryStatus;
import org.sda.deliveryservice.entity.GeoPoint;
import org.sda.deliveryservice.entity.Rider;
import org.sda.deliveryservice.publisher.DeliveryEventPublisher;
import org.sda.deliveryservice.repository.DeliveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The delivery lifecycle: create from an event, auto-assign a rider, track the ride, publish
 * progress.
 *
 * <p>Every inbound handler is written to be safely re-runnable, because RabbitMQ can redeliver a
 * message and because the assignment sweep revisits the same rows repeatedly. Out-of-order or
 * duplicate input is logged and ignored rather than throwing, which would only bounce the message.
 */
@Service
public class DeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);

    private static final List<DeliveryStatus> LIVE_STATUSES =
            List.of(DeliveryStatus.ASSIGNED, DeliveryStatus.ACCEPTED, DeliveryStatus.PICKED_UP);

    private final DeliveryRepository deliveryRepository;
    private final RiderService riderService;
    private final RouteEstimator routeEstimator;
    private final DeliveryEventPublisher publisher;

    @Value("${delivery.arrival-radius-km}")
    private double arrivalRadiusKm;

    public DeliveryService(DeliveryRepository deliveryRepository,
                           RiderService riderService,
                           RouteEstimator routeEstimator,
                           DeliveryEventPublisher publisher) {
        this.deliveryRepository = deliveryRepository;
        this.riderService = riderService;
        this.routeEstimator = routeEstimator;
        this.publisher = publisher;
    }

    // ------------------------------------------------------------------
    // Inbound events
    // ------------------------------------------------------------------

    /** Handles {@code restaurant.order-ready}: create the job, then try to staff it immediately. */
    public void onOrderReady(OrderReadyEvent event) {
        Optional<Delivery> existing = deliveryRepository.findByOrderId(event.orderId());
        if (existing.isPresent()) {
            log.warn("Delivery for order {} already exists; ignoring duplicate order-ready event", event.orderId());
            return;
        }

        Delivery delivery = new Delivery();
        delivery.setId(UUID.randomUUID().toString());
        delivery.setOrderId(event.orderId());
        delivery.setUserId(event.userId());
        delivery.setRestaurantId(event.restaurantId());
        delivery.setPickup(new GeoPoint(event.pickupLatitude(), event.pickupLongitude()));
        delivery.setDrop(new GeoPoint(event.dropLatitude(), event.dropLongitude()));
        delivery.setDropAddressLabel(event.dropAddressLabel());
        delivery.setStatus(DeliveryStatus.PENDING_ASSIGNMENT);
        deliveryRepository.save(delivery);

        log.info("Delivery {} created for order {}", delivery.getId(), delivery.getOrderId());
        tryAssign(delivery);
    }

    /** Handles {@code order.cancelled}: stop the job and give the rider back to the pool. */
    public void onOrderCancelled(String orderId) {
        Optional<Delivery> found = deliveryRepository.findByOrderId(orderId);
        if (found.isEmpty()) {
            // Normal for orders cancelled before the food was ever cooked.
            log.info("No delivery exists for cancelled order {}; nothing to release", orderId);
            return;
        }

        Delivery delivery = found.get();
        if (delivery.getStatus() == DeliveryStatus.DELIVERED) {
            log.warn("Order {} was cancelled but delivery {} is already DELIVERED; leaving it alone",
                    orderId, delivery.getId());
            return;
        }
        if (delivery.getStatus() == DeliveryStatus.CANCELLED) {
            return;
        }

        riderService.release(delivery.getRiderId(), false);
        delivery.setStatus(DeliveryStatus.CANCELLED);
        touch(delivery);
        log.info("Delivery {} cancelled with order {}", delivery.getId(), orderId);
    }

    // ------------------------------------------------------------------
    // Automatic assignment
    // ------------------------------------------------------------------

    /**
     * Picks the nearest free rider and offers them the job.
     *
     * <p>Synchronised because the event listener and the retry sweep can both reach this at the
     * same time and would otherwise be able to hand one rider two jobs. That is enough for a
     * single instance; running several copies of this service would need an atomic
     * find-and-modify on the rider document instead.
     */
    public synchronized boolean tryAssign(Delivery delivery) {
        if (delivery.getStatus() != DeliveryStatus.PENDING_ASSIGNMENT) {
            return false;
        }

        Optional<Rider> candidate = riderService.findNearestAvailable(
                delivery.getPickup(), delivery.getDeclinedByRiderIds());
        if (candidate.isEmpty()) {
            log.info("No rider available for delivery {} yet; will retry on the next sweep", delivery.getId());
            return false;
        }

        Rider rider = riderService.reserve(candidate.get(), delivery.getId());

        delivery.setRiderId(rider.getId());
        delivery.setRiderDisplayName(rider.getDisplayName());
        delivery.setRiderPhone(rider.getPhone());
        delivery.setRiderLocation(rider.getLocation());
        delivery.setRiderLocationUpdatedAt(rider.getLocationUpdatedAt());
        delivery.setStatus(DeliveryStatus.ASSIGNED);
        delivery.setAssignedAt(Instant.now());
        // Before pickup the rider still has to reach the restaurant, so the ETA spans both legs.
        applyEstimate(delivery, routeEstimator.distanceKm(rider.getLocation(), delivery.getPickup())
                + routeEstimator.distanceKm(delivery.getPickup(), delivery.getDrop()));
        touch(delivery);

        log.info("Delivery {} assigned to rider {} ({} min ETA)",
                delivery.getId(), rider.getId(), delivery.getEtaMinutes());
        publisher.publishAssigned(delivery);
        return true;
    }

    /** Re-attempts every job that had no rider free when it was created. */
    public void sweepUnassigned() {
        List<Delivery> waiting = deliveryRepository.findByStatus(DeliveryStatus.PENDING_ASSIGNMENT);
        if (waiting.isEmpty()) {
            return;
        }
        log.debug("Assignment sweep: {} delivery(ies) waiting for a rider", waiting.size());
        waiting.forEach(this::tryAssign);
    }

    // ------------------------------------------------------------------
    // Rider app actions
    // ------------------------------------------------------------------

    public Delivery accept(String deliveryId, String riderId) {
        Delivery delivery = requireOwnedBy(deliveryId, riderId);
        if (delivery.getStatus() != DeliveryStatus.ASSIGNED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only an ASSIGNED delivery can be accepted (current status: " + delivery.getStatus() + ")");
        }
        delivery.setStatus(DeliveryStatus.ACCEPTED);
        delivery.setAcceptedAt(Instant.now());
        return touch(delivery);
    }

    /**
     * The rider turns the job down. It goes back into the pool, and this rider is remembered so
     * the next attempt skips them.
     */
    public Delivery decline(String deliveryId, String riderId) {
        Delivery delivery = requireOwnedBy(deliveryId, riderId);
        if (delivery.getStatus() != DeliveryStatus.ASSIGNED && delivery.getStatus() != DeliveryStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A delivery can only be declined before pickup (current status: " + delivery.getStatus() + ")");
        }

        riderService.release(riderId, false);
        delivery.getDeclinedByRiderIds().add(riderId);
        delivery.setRiderId(null);
        delivery.setRiderDisplayName(null);
        delivery.setRiderPhone(null);
        delivery.setRiderLocation(null);
        delivery.setRiderLocationUpdatedAt(null);
        delivery.setRemainingDistanceKm(null);
        delivery.setEtaMinutes(null);
        delivery.setEtaAt(null);
        delivery.setAssignedAt(null);
        delivery.setAcceptedAt(null);
        delivery.setStatus(DeliveryStatus.PENDING_ASSIGNMENT);
        Delivery saved = touch(delivery);

        log.info("Rider {} declined delivery {}; searching for another rider", riderId, deliveryId);
        tryAssign(saved);
        return saved;
    }

    public Delivery markPickedUp(String deliveryId, String riderId) {
        Delivery delivery = requireOwnedBy(deliveryId, riderId);
        if (delivery.getStatus() != DeliveryStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Accept the delivery before marking it picked up (current status: " + delivery.getStatus() + ")");
        }
        delivery.setStatus(DeliveryStatus.PICKED_UP);
        delivery.setPickedUpAt(Instant.now());
        // The restaurant leg is done; from here the ETA is only the ride to the customer.
        GeoPoint from = delivery.getRiderLocation() == null ? delivery.getPickup() : delivery.getRiderLocation();
        applyEstimate(delivery, routeEstimator.distanceKm(from, delivery.getDrop()));
        Delivery saved = touch(delivery);

        publisher.publishStarted(saved);
        return saved;
    }

    public Delivery markDelivered(String deliveryId, String riderId) {
        Delivery delivery = requireOwnedBy(deliveryId, riderId);
        if (delivery.getStatus() != DeliveryStatus.PICKED_UP) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only a PICKED_UP delivery can be marked delivered (current status: " + delivery.getStatus() + ")");
        }
        delivery.setStatus(DeliveryStatus.DELIVERED);
        delivery.setDeliveredAt(Instant.now());
        delivery.setRemainingDistanceKm(0.0);
        delivery.setEtaMinutes(0);
        delivery.setEtaAt(Instant.now());
        Delivery saved = touch(delivery);

        riderService.release(riderId, true);
        publisher.publishCompleted(saved);
        return saved;
    }

    // ------------------------------------------------------------------
    // Live tracking
    // ------------------------------------------------------------------

    /**
     * Stores a GPS ping and, if the rider is mid-job, refreshes the distance and ETA the customer
     * sees. The first ping inside the arrival radius also raises {@code delivery.arriving}.
     */
    public Rider recordRiderLocation(String riderId, double latitude, double longitude) {
        Rider rider = riderService.recordLocation(riderId, latitude, longitude);

        deliveryRepository.findByRiderIdAndStatusIn(riderId, LIVE_STATUSES).ifPresent(delivery -> {
            delivery.setRiderLocation(rider.getLocation());
            delivery.setRiderLocationUpdatedAt(rider.getLocationUpdatedAt());

            double remainingKm = delivery.getStatus() == DeliveryStatus.PICKED_UP
                    ? routeEstimator.distanceKm(rider.getLocation(), delivery.getDrop())
                    : routeEstimator.distanceKm(rider.getLocation(), delivery.getPickup())
                            + routeEstimator.distanceKm(delivery.getPickup(), delivery.getDrop());
            applyEstimate(delivery, remainingKm);

            boolean nearlyThere = delivery.getStatus() == DeliveryStatus.PICKED_UP
                    && remainingKm <= arrivalRadiusKm
                    && !delivery.isArrivalNotified();
            if (nearlyThere) {
                delivery.setArrivalNotified(true);
            }

            Delivery saved = touch(delivery);
            if (nearlyThere) {
                publisher.publishArriving(saved);
            }
        });

        return rider;
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    public Delivery getById(String deliveryId) {
        return deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivery not found"));
    }

    public Delivery getByOrderId(String orderId) {
        return deliveryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No delivery has been created for this order yet"));
    }

    public Optional<Delivery> findCurrentAssignment(String riderId) {
        return deliveryRepository.findByRiderIdAndStatusIn(riderId, LIVE_STATUSES);
    }

    public List<Delivery> findByRider(String riderId) {
        return deliveryRepository.findByRiderIdOrderByCreatedAtDesc(riderId);
    }

    public List<Delivery> findByCustomer(String userId) {
        return deliveryRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<Delivery> findAll() {
        return deliveryRepository.findAll();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void applyEstimate(Delivery delivery, double remainingKm) {
        int minutes = routeEstimator.etaMinutes(remainingKm);
        delivery.setRemainingDistanceKm(round(remainingKm));
        delivery.setEtaMinutes(minutes);
        delivery.setEtaAt(routeEstimator.etaAt(minutes));
    }

    private Delivery requireOwnedBy(String deliveryId, String riderId) {
        Delivery delivery = getById(deliveryId);
        if (!riderId.equals(delivery.getRiderId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This delivery is not assigned to you");
        }
        return delivery;
    }

    private Delivery touch(Delivery delivery) {
        delivery.setUpdatedAt(Instant.now());
        return deliveryRepository.save(delivery);
    }

    private double round(double km) {
        return Math.round(km * 100) / 100.0;
    }
}
