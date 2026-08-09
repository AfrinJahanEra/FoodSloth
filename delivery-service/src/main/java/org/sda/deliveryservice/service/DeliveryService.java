package org.sda.deliveryservice.service;

import org.sda.deliveryservice.dto.event.OrderReadyEvent;
import org.sda.deliveryservice.entity.Delivery;
import org.sda.deliveryservice.entity.DeliveryStatus;
import org.sda.deliveryservice.entity.GeoPoint;
import org.sda.deliveryservice.entity.Rider;
import org.sda.deliveryservice.entity.RiderStatus;
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
 * The delivery lifecycle: create from an event, get a rider onto it, track the ride, publish
 * progress.
 *
 * <p>Riders are staffed by the admin, who watches the online-rider board and assigns a free rider
 * to each waiting job ({@link #assignByAdmin}). An online rider cannot refuse an assignment -
 * they accept it, ride it out and mark it delivered. With {@code delivery.auto-assign} switched
 * on the service additionally staffs jobs itself, nearest-free-rider first.
 *
 * <p>Every inbound handler is written to be safely re-runnable, because RabbitMQ can redeliver a
 * message. Out-of-order or duplicate input is logged and ignored rather than throwing, which would
 * only bounce the message.
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
    private final AddressGeocoder geocoder;

    /** False by default: the admin assigns riders by hand; true restores nearest-rider auto-staffing. */
    @Value("${delivery.auto-assign}")
    private boolean autoAssign;

    public DeliveryService(DeliveryRepository deliveryRepository,
                           RiderService riderService,
                           RouteEstimator routeEstimator,
                           DeliveryEventPublisher publisher,
                           AddressGeocoder geocoder) {
        this.deliveryRepository = deliveryRepository;
        this.riderService = riderService;
        this.routeEstimator = routeEstimator;
        this.publisher = publisher;
        this.geocoder = geocoder;
    }

    // ------------------------------------------------------------------
    // Inbound events
    // ------------------------------------------------------------------

    /** Handles {@code restaurant.order-ready}: create the job, waiting for the admin to staff it. */
    public void onOrderReady(OrderReadyEvent event) {
        Optional<Delivery> existing = deliveryRepository.findByOrderId(event.orderId());
        if (existing.isPresent()) {
            log.warn("Delivery for order {} already exists; ignoring duplicate order-ready event", event.orderId());
            return;
        }

        Delivery delivery = new Delivery();
        delivery.setId(UUID.randomUUID().toString());
        delivery.setOrderId(event.orderId());
        delivery.setOrderNo(event.orderNo());
        delivery.setUserId(event.userId());
        delivery.setRestaurantId(event.restaurantId());
        delivery.setPickup(new GeoPoint(event.pickupLatitude(), event.pickupLongitude()));
        delivery.setDrop(new GeoPoint(event.dropLatitude(), event.dropLongitude()));
        delivery.setDropAddressLabel(event.dropAddressLabel());
        resolveDrop(delivery);
        delivery.setCustomerPhone(event.customerPhone());
        delivery.setStatus(DeliveryStatus.PENDING_ASSIGNMENT);
        deliveryRepository.save(delivery);

        log.info("Delivery {} created for order {}; waiting for the admin to assign a rider",
                delivery.getId(), delivery.getOrderId());
        // Default mode parks the job for the admin board; auto-assign is an opt-in fallback.
        if (autoAssign) {
            tryAssign(delivery);
        }
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
    // Assignment - admin first, automatic only when switched on
    // ------------------------------------------------------------------

    /**
     * The admin hands a waiting job to a specific rider. The rider must be online and must still
     * have a free slot for today - an online rider cannot refuse, acceptance is mandatory.
     */
    public synchronized Delivery assignByAdmin(String deliveryId, String riderId) {
        Delivery delivery = getById(deliveryId);
        if (delivery.getStatus() != DeliveryStatus.PENDING_ASSIGNMENT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only a PENDING_ASSIGNMENT delivery can be assigned (current status: " + delivery.getStatus() + ")");
        }

        Rider rider = riderService.require(riderId);
        if (rider.getStatus() == RiderStatus.OFFLINE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Rider " + rider.getDisplayName() + " is offline and cannot take deliveries");
        }
        if (!riderService.hasFreeSlotToday(rider)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Rider " + rider.getDisplayName() + " has already used all of today's delivery slots");
        }

        return attachRider(delivery, rider, "admin");
    }

    /**
     * Automatic staffing, nearest free rider first - only used when {@code delivery.auto-assign}
     * is switched on.
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

        attachRider(delivery, candidate.get(), "auto-assign");
        return true;
    }

    /** Shared tail of both assignment paths: reserve the rider, snapshot them onto the job, publish. */
    private Delivery attachRider(Delivery delivery, Rider candidate, String assignedBy) {
        Rider rider = riderService.reserve(candidate, delivery.getId());

        delivery.setRiderId(rider.getId());
        delivery.setRiderDisplayName(rider.getDisplayName());
        delivery.setRiderPhone(rider.getPhone());
        GeoPoint riderFix = rider.getLocation() != null && rider.getLocation().isReal()
                ? rider.getLocation() : null;
        delivery.setRiderLocation(riderFix);
        delivery.setRiderLocationUpdatedAt(rider.getLocationUpdatedAt());
        delivery.setStatus(DeliveryStatus.ASSIGNED);
        delivery.setAssignedAt(Instant.now());
        // Before pickup the rider still has to reach the restaurant, so the ETA spans both legs.
        // Without a believable rider fix the first leg is measured from the restaurant itself.
        estimateFor(delivery, riderFix, false);
        Delivery saved = touch(delivery);

        log.info("Delivery {} assigned to rider {} by {} ({} min ETA)",
                delivery.getId(), rider.getId(), assignedBy, delivery.getEtaMinutes());
        publisher.publishAssigned(saved);
        return saved;
    }

    /** Re-attempts every job that still has no rider - only meaningful in auto-assign mode. */
    public void sweepUnassigned() {
        if (!autoAssign) {
            return;
        }
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

    /**
     * Live GPS push from the rider app. The fix is stored on the rider and mirrored onto every
     * open job, refreshing the remaining distance and ETA so the customer's tracking map follows
     * the ride in real time. No event is published here - tracking pages poll the REST feed.
     */
    public void updateRiderLocation(String riderId, double latitude, double longitude) {
        Rider rider = riderService.updateLocation(riderId, latitude, longitude);
        GeoPoint fix = rider.getLocation();
        if (fix == null) {
            return; // sentinel fix - keep the last known position and estimates
        }
        Instant now = rider.getLocationUpdatedAt();
        List<Delivery> liveJobs =
                deliveryRepository.findByRiderIdAndStatusInOrderByAssignedAtAsc(riderId, LIVE_STATUSES);
        for (Delivery delivery : liveJobs) {
            delivery.setRiderLocation(fix);
            delivery.setRiderLocationUpdatedAt(now);
            boolean pickedUp = delivery.getStatus() == DeliveryStatus.PICKED_UP;
            // Before pickup the rider still owes the restaurant leg; after it, only the drop leg.
            estimateFor(delivery, fix, pickedUp);
            touch(delivery);
        }
    }

    /**
     * The rider confirms the assignment. This is the moment the customer's order turns
     * OUT_FOR_DELIVERY: {@code delivery.started} is published here, not at pickup, because an
     * online rider is expected to ride the job out - there is no decline path.
     */
    public Delivery accept(String deliveryId, String riderId) {
        Delivery delivery = requireOwnedBy(deliveryId, riderId);
        if (delivery.getStatus() != DeliveryStatus.ASSIGNED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only an ASSIGNED delivery can be accepted (current status: " + delivery.getStatus() + ")");
        }
        delivery.setStatus(DeliveryStatus.ACCEPTED);
        delivery.setAcceptedAt(Instant.now());
        Delivery saved = touch(delivery);

        publisher.publishStarted(saved);
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
        estimateFor(delivery, delivery.getRiderLocation(), true);
        // No event here: the order already turned OUT_FOR_DELIVERY when the rider accepted.
        return touch(delivery);
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
    // Admin actions
    // ------------------------------------------------------------------

    /**
     * The admin cancels a delivery job. Riders deliberately have no cancel path - only the admin
     * can call this. The job's slot is given back to the rider (the work was never done), so the
     * rider becomes assignable again immediately.
     */
    public synchronized Delivery cancelByAdmin(String deliveryId) {
        Delivery delivery = getById(deliveryId);
        if (delivery.getStatus() == DeliveryStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This delivery is already cancelled");
        }
        if (delivery.getStatus() == DeliveryStatus.DELIVERED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A delivered order cannot be cancelled - refund it through Payments instead");
        }

        // Give the reserved slot back; release() is a no-op when no rider was attached yet.
        riderService.release(delivery.getRiderId(), false);
        delivery.setStatus(DeliveryStatus.CANCELLED);
        Delivery saved = touch(delivery);
        log.info("Delivery {} for order {} cancelled by the admin; rider slot freed",
                delivery.getId(), delivery.getOrderId());
        return saved;
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
        // A rider can hold several live jobs (assigned while a slot was free); the rider works
        // them off oldest first, so the oldest live delivery is the current job.
        return deliveryRepository.findByRiderIdAndStatusInOrderByAssignedAtAsc(riderId, LIVE_STATUSES)
                .stream().findFirst();
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

    /**
     * Refreshes remaining km / ETA using real coordinates only. A leg whose pin is the (0,0)
     * sentinel is never fed into the math - it would print ten-thousand-kilometre ETAs. When
     * nothing real is measurable the estimate is cleared so the apps show an honest dash.
     */
    private void estimateFor(Delivery delivery, GeoPoint riderFix, boolean pickedUp) {
        resolveDrop(delivery);
        GeoPoint pickup = delivery.getPickup();
        GeoPoint drop = delivery.getDrop();
        boolean pickupReal = pickup != null && pickup.isReal();
        boolean dropReal = drop != null && drop.isReal();

        double remaining;
        if (pickedUp) {
            // Only the drop leg remains; without a real start point there is nothing to measure.
            GeoPoint from = riderFix != null ? riderFix : (pickupReal ? pickup : null);
            if (from == null || !dropReal) {
                clearEstimate(delivery);
                return;
            }
            remaining = routeEstimator.distanceKm(from, drop);
        } else {
            // The rider still owes the restaurant leg; the drop leg is added when its pin exists.
            if (!pickupReal) {
                clearEstimate(delivery);
                return;
            }
            GeoPoint from = riderFix != null ? riderFix : pickup;
            remaining = routeEstimator.distanceKm(from, pickup)
                    + (dropReal ? routeEstimator.distanceKm(pickup, drop) : 0);
        }
        applyEstimate(delivery, remaining);
    }

    /**
     * Orders placed on an address without a GPS pin arrive with the (0,0) sentinel drop.
     * Resolve the address text to a real pin once (cached, so at most one lookup per
     * address) so the distance/ETA math has a destination to measure to.
     */
    private void resolveDrop(Delivery delivery) {
        GeoPoint drop = delivery.getDrop();
        if (drop != null && drop.isReal()) {
            return;
        }
        geocoder.geocode(delivery.getDropAddressLabel()).ifPresent(delivery::setDrop);
    }

    private void clearEstimate(Delivery delivery) {
        delivery.setRemainingDistanceKm(null);
        delivery.setEtaMinutes(null);
        delivery.setEtaAt(null);
    }

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
