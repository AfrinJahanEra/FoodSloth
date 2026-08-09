package org.sda.deliveryservice.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * A rider as Delivery Service knows them.
 *
 * <p>{@code id} is the user id minted by User Service and carried in the {@code X-User-Id} header,
 * so the two stay in step without this service ever calling User Service. {@code displayName} and
 * {@code phone} are supplied by the rider app when the rider goes online - they are what the
 * customer is shown while tracking, not a copy of the User Service account record.
 */
@Document(collection = "riders")
@Data
@NoArgsConstructor
public class Rider {

    /** Same value as the user id issued by User Service. */
    @Id
    private String id;

    @Field
    private String displayName;

    @Field
    private String phone;

    @Field
    private String vehicleType;

    @Field
    @Indexed
    private RiderStatus status = RiderStatus.OFFLINE;

    /** Last GPS ping. Null until the rider first goes online. */
    @Field
    private GeoPoint location;

    @Field
    private Instant locationUpdatedAt;

    /** The delivery this rider is currently holding, or null when free. */
    @Field
    private String activeDeliveryId;

    @Field
    private int completedDeliveries;

    @Field
    private Instant createdAt = Instant.now();

    @Field
    private Instant updatedAt = Instant.now();
}
