package org.sda.orderservice.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The order document. Its id <em>is</em> the orderId that Cart Service minted at checkout, which is
 * the correlation key every event about this order carries - so the client can poll
 * {@code GET /orders/{orderId}} from the moment checkout returns.
 *
 * <p>Everything here comes from the {@code restaurant.order-priced} snapshot: this service never
 * prices anything itself and holds no menu data.
 */
@Document(collection = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    @Id
    private String id;

    /** Sequential human-facing number shown as #123 in the UI; the UUID id stays the correlation key. */
    @Field
    @Indexed(unique = true, sparse = true)
    private Long orderNo;

    @Field
    @Indexed
    private String userId;

    @Field
    private String restaurantId;

    @Field
    private String restaurantName;

    @Field
    private List<OrderItem> items = new ArrayList<>();

    @Field
    private BigDecimal subtotal;

    @Field
    private BigDecimal deliveryCharge;

    @Field
    private BigDecimal tax;

    @Field
    private BigDecimal grandTotal;

    @Field
    private String currency;

    @Field
    private PaymentMethod paymentMethod;

    // ---- Drop-off, snapshotted at checkout so a later address edit cannot move a live order ----

    @Field
    private String deliveryAddress;

    @Field
    private Double deliveryLatitude;

    @Field
    private Double deliveryLongitude;

    @Field
    private String note;

    @Field
    private OrderStatus status;

    @Field
    private Instant createdAt;

    @Field
    private Instant updatedAt;
}
