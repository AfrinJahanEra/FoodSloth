package com.service.cart_service.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "carts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Cart {

    @Id
    private String id;

    @Field
    @Indexed(unique = true)
    private String userId;

    @Field
    private List<CartItem> items = new ArrayList<>();

    @Field
    private String couponCode;

    @Field
    private double subtotal;

    @Field
    private double tax;

    @Field
    private double deliveryFee;

    @Field
    private double total;

    @Field
    private Instant createdAt = Instant.now();

    @Field
    private Instant updatedAt = Instant.now();
}
