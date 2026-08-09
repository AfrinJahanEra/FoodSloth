package org.sda.deliveryservice.controller;

import org.sda.deliveryservice.dto.AssignRiderRequest;
import org.sda.deliveryservice.dto.DeliveryResponse;
import org.sda.deliveryservice.dto.GoOnlineRequest;
import org.sda.deliveryservice.dto.LocationUpdateRequest;
import org.sda.deliveryservice.dto.RiderResponse;
import org.sda.deliveryservice.dto.TrackingResponse;
import org.sda.deliveryservice.entity.Delivery;
import org.sda.deliveryservice.service.DeliveryService;
import org.sda.deliveryservice.service.RiderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * REST surface of Delivery Service - the rider app on one side, the customer's tracking map on the
 * other. Reached by clients through api-gateway at {@code /deliveries/**}.
 *
 * <p>Auth note: the JWT is verified by api-gateway, not here. The gateway strips any
 * client-supplied {@code X-User-*} headers and re-adds them from the verified claims, so this
 * service trusts {@code X-User-Id} / {@code X-User-Role} instead of parsing tokens itself.
 *
 * <p>No endpoint here is called by another service - services talk to Delivery Service only by
 * publishing to {@code food.exchange}.
 */
@RestController
@RequestMapping("/deliveries")
public class DeliveryController {

    private static final String ROLE_RIDER = "DELIVERYMAN";
    private static final String ROLE_ADMIN = "ADMIN";

    private final DeliveryService deliveryService;
    private final RiderService riderService;

    public DeliveryController(DeliveryService deliveryService, RiderService riderService) {
        this.deliveryService = deliveryService;
        this.riderService = riderService;
    }

    // ------------------------------------------------------------------
    // Rider app - shift and position
    // ------------------------------------------------------------------

    @PostMapping("/riders/online")
    public RiderResponse goOnline(@RequestHeader(value = "X-User-Id", required = false) String userId,
                          @RequestHeader(value = "X-User-Role", required = false) String role,
                          @RequestBody GoOnlineRequest request) {
        String riderId = requireRider(userId, role);
        requireCoordinates(request.latitude(), request.longitude());
        return riderService.toResponse(riderService.goOnline(riderId, request.displayName(), request.phone(), request.vehicleType(),
                request.latitude(), request.longitude()));
    }

    @PostMapping("/riders/offline")
    public RiderResponse goOffline(@RequestHeader(value = "X-User-Id", required = false) String userId,
                           @RequestHeader(value = "X-User-Role", required = false) String role) {
        return riderService.toResponse(riderService.goOffline(requireRider(userId, role)));
    }

    @GetMapping("/riders/me")
    public RiderResponse getMyRiderProfile(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                   @RequestHeader(value = "X-User-Role", required = false) String role) {
        return riderService.toResponse(riderService.require(requireRider(userId, role)));
    }

    /**
     * Live GPS push from the rider app. The fix is mirrored onto the open job so the rider's own
     * map and the customer's tracking page both follow the ride in real time.
     */
    @PostMapping("/riders/me/location")
    public RiderResponse updateMyLocation(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                  @RequestHeader(value = "X-User-Role", required = false) String role,
                                  @RequestBody LocationUpdateRequest request) {
        String riderId = requireRider(userId, role);
        requireCoordinates(request.latitude(), request.longitude());
        deliveryService.updateRiderLocation(riderId, request.latitude(), request.longitude());
        return riderService.toResponse(riderService.require(riderId));
    }

    // ------------------------------------------------------------------
    // Rider app - the job
    // ------------------------------------------------------------------

    /** The job the rider is on right now, or 204 when there is nothing to do. */
    @GetMapping("/riders/me/current")
    public ResponseEntity<DeliveryResponse> getCurrentAssignment(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        String riderId = requireRider(userId, role);
        return deliveryService.findCurrentAssignment(riderId)
                .map(delivery -> ResponseEntity.ok(DeliveryResponse.from(delivery)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/riders/me/history")
    public List<DeliveryResponse> getMyHistory(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                      @RequestHeader(value = "X-User-Role", required = false) String role) {
        return deliveryService.findByRider(requireRider(userId, role)).stream().map(DeliveryResponse::from).toList();
    }

    /**
     * The rider confirms an assignment. Online riders cannot refuse a job - accepting is the only
     * way forward, and this is the moment the customer's order turns OUT_FOR_DELIVERY.
     */
    @PatchMapping("/{deliveryId}/accept")
    public DeliveryResponse accept(@RequestHeader(value = "X-User-Id", required = false) String userId,
                           @RequestHeader(value = "X-User-Role", required = false) String role,
                           @PathVariable String deliveryId) {
        return DeliveryResponse.from(deliveryService.accept(deliveryId, requireRider(userId, role)));
    }

    @PatchMapping("/{deliveryId}/picked-up")
    public DeliveryResponse markPickedUp(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                 @RequestHeader(value = "X-User-Role", required = false) String role,
                                 @PathVariable String deliveryId) {
        return DeliveryResponse.from(deliveryService.markPickedUp(deliveryId, requireRider(userId, role)));
    }

    @PatchMapping("/{deliveryId}/delivered")
    public DeliveryResponse markDelivered(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                  @RequestHeader(value = "X-User-Role", required = false) String role,
                                  @PathVariable String deliveryId) {
        return DeliveryResponse.from(deliveryService.markDelivered(deliveryId, requireRider(userId, role)));
    }

    // ------------------------------------------------------------------
    // Customer - live tracking
    // ------------------------------------------------------------------

    /**
     * Feed behind the customer's map: rider position, remaining distance and ETA. Polled every few
     * seconds by the client, which draws the marker itself.
     */
    @GetMapping("/order/{orderId}/track")
    public TrackingResponse track(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                  @RequestHeader(value = "X-User-Role", required = false) String role,
                                  @PathVariable String orderId) {
        requireAuthenticated(userId);
        Delivery delivery = deliveryService.getByOrderId(orderId);
        boolean ownParcel = userId.equals(delivery.getUserId());
        boolean staffOnTheJob = userId.equals(delivery.getRiderId()) || ROLE_ADMIN.equals(role);
        if (!ownParcel && !staffOnTheJob) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This delivery belongs to another customer");
        }
        return TrackingResponse.from(delivery);
    }

    @GetMapping("/me")
    public List<DeliveryResponse> getMyDeliveries(@RequestHeader(value = "X-User-Id", required = false) String userId) {
        return deliveryService.findByCustomer(requireAuthenticated(userId)).stream().map(DeliveryResponse::from).toList();
    }

    // ------------------------------------------------------------------
    // Admin
    // ------------------------------------------------------------------

    @GetMapping
    public List<DeliveryResponse> getAllDeliveries(@RequestHeader(value = "X-User-Role", required = false) String role) {
        requireAdmin(role);
        return deliveryService.findAll().stream().map(DeliveryResponse::from).toList();
    }

    @GetMapping("/riders")
    public List<RiderResponse> getAllRiders(@RequestHeader(value = "X-User-Role", required = false) String role) {
        requireAdmin(role);
        return riderService.findAll().stream().map(riderService::toResponse).toList();
    }

    /**
     * The admin hands a waiting delivery to a chosen rider. The rider must be online and have a
     * free slot for today; the assignment is mandatory for the rider.
     */
    @PostMapping("/{deliveryId}/assign")
    public DeliveryResponse assign(@RequestHeader(value = "X-User-Role", required = false) String role,
                           @PathVariable String deliveryId,
                           @RequestBody AssignRiderRequest request) {
        requireAdmin(role);
        if (request == null || request.riderId() == null || request.riderId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "riderId is required");
        }
        return DeliveryResponse.from(deliveryService.assignByAdmin(deliveryId, request.riderId()));
    }

    @GetMapping("/{deliveryId}")
    public DeliveryResponse getDelivery(@RequestHeader(value = "X-User-Role", required = false) String role,
                                @PathVariable String deliveryId) {
        requireAdmin(role);
        return DeliveryResponse.from(deliveryService.getById(deliveryId));
    }

    /**
     * The admin cancels a delivery job. Only the admin has this power - riders have no cancel
     * endpoint. The rider's slot for the job is handed back, so they can be assigned again at once.
     */
    @PatchMapping("/{deliveryId}/cancel")
    public DeliveryResponse cancel(@RequestHeader(value = "X-User-Role", required = false) String role,
                           @PathVariable String deliveryId) {
        requireAdmin(role);
        return DeliveryResponse.from(deliveryService.cancelByAdmin(deliveryId));
    }

    // ------------------------------------------------------------------
    // Gateway-header guards
    // ------------------------------------------------------------------

    private String requireAuthenticated(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization token");
        }
        return userId;
    }

    private String requireRider(String userId, String role) {
        requireAuthenticated(userId);
        if (!ROLE_RIDER.equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Delivery rider role required");
        }
        return userId;
    }

    private void requireAdmin(String role) {
        if (role == null || role.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization token");
        }
        if (!ROLE_ADMIN.equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
        }
    }

    private void requireCoordinates(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "latitude and longitude are required");
        }
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "latitude/longitude out of range");
        }
    }
}
