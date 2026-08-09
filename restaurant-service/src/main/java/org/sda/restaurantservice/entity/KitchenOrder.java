package org.sda.restaurantservice.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.sda.restaurantservice.dto.event.PricedItem;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The kitchen's own record of one order: what to cook, what it was priced at, and where it is going.
 *
 * <p>The document id <em>is</em> the {@code orderId} minted by Cart Service. That makes the ticket
 * idempotent for free - a redelivered {@code cart.checked-out} overwrites the same document instead
 * of creating a second ticket, and every later event finds its ticket by the same key.
 *
 * <p>This is not a copy of the order. Order Service owns the order's status, the customer's payment
 * and its history; this document holds only what a kitchen needs to work from, plus the price
 * snapshot this service itself produced.
 */
@Document(collection = "kitchen_orders")
@Data
@NoArgsConstructor
public class KitchenOrder {

    /** The orderId from checkout - the correlation key used by every event about this order. */
    @Id
    private String id;

    @Field
    @Indexed
    private String userId;

    @Field
    private String restaurantId;

    @Field
    private List<PricedItem> items = new ArrayList<>();

    @Field
    private double itemsTotal;

    @Field
    private double deliveryFee;

    @Field
    private double tax;

    @Field
    private double grandTotal;

    @Field
    private String currency;

    @Field
    @Indexed
    private KitchenOrderStatus status = KitchenOrderStatus.AWAITING_PAYMENT;

    /** Why the kitchen turned it down, or why it was cancelled. */
    @Field
    private String statusReason;

    @Field
    private String note;

    // ---- Drop-off, snapshotted at checkout so a later address edit cannot move a live order ----

    @Field
    private String dropAddressLabel;

    @Field
    private Double dropLatitude;

    @Field
    private Double dropLongitude;

    @Field
    private Instant createdAt = Instant.now();

    @Field
    private Instant updatedAt = Instant.now();

    @Field
    private Instant acceptedAt;

    @Field
    private Instant readyAt;
}
