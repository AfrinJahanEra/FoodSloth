package org.sda.deliveryservice.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One delivery job, created when Restaurant Service announces the food is ready.
 *
 * <p>Everything needed to run the job arrives in that event - pickup and drop coordinates, the
 * customer id, the order id. Delivery Service never reads Order Service, Restaurant Service or
 * User Service to fill anything in.
 */
@Document(collection = "deliveries")
@Data
@NoArgsConstructor
public class Delivery {

    @Id
    private String id;

    /** Correlation key shared by every service touching this order. */
    @Field
    @Indexed(unique = true)
    private String orderId;

    /** The customer waiting for the food. */
    @Field
    @Indexed
    private String userId;

    @Field
    private String restaurantId;

    @Field
    private GeoPoint pickup;

    @Field
    private GeoPoint drop;

    /** Kept only so the rider app can show the address the customer already picked. */
    @Field
    private String dropAddressLabel;

    @Field
    @Indexed
    private DeliveryStatus status = DeliveryStatus.PENDING_ASSIGNMENT;

    @Field
    @Indexed
    private String riderId;

    @Field
    private String riderDisplayName;

    @Field
    private String riderPhone;

    /** Riders who declined this job; they are skipped on the next assignment attempt. */
    @Field
    private List<String> declinedByRiderIds = new ArrayList<>();

    // ---- Live tracking ----

    @Field
    private GeoPoint riderLocation;

    @Field
    private Instant riderLocationUpdatedAt;

    /** Straight-line kilometres still to ride, recomputed on every GPS ping. */
    @Field
    private Double remainingDistanceKm;

    @Field
    private Integer etaMinutes;

    /** Absolute arrival estimate, so a stale response is obvious to the client. */
    @Field
    private Instant etaAt;

    /** Guard so the "rider is arriving" push is sent once, not on every ping. */
    @Field
    private boolean arrivalNotified;

    // ---- Timeline ----

    @Field
    private Instant assignedAt;

    @Field
    private Instant acceptedAt;

    @Field
    private Instant pickedUpAt;

    @Field
    private Instant deliveredAt;

    @Field
    private Instant createdAt = Instant.now();

    @Field
    private Instant updatedAt = Instant.now();
}
