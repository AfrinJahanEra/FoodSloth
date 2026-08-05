package org.sda.orderservice.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    @Id
    private String id;

    @Field
    private String userId;

    @Field
    private String restaurantId;

    @Field
    private String deliveryAddressId;

    @Field
    private DeliveryType deliveryType;

    @Field
    private PaymentMethod paymentMethod;

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
    private OrderStatus status;

    @Field
    private Instant createdAt;

    @Field
    private Instant updatedAt;
}
