package org.sda.restaurantservice.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Single-tenant: exactly one document ever exists in this collection - the one restaurant
 * this whole service is built for (KFC-app style), not a multi-restaurant marketplace.
 */
@Document(collection = "restaurant")
@Data
@NoArgsConstructor
public class Restaurant {
    @Id
    private String id;

    @Field
    private String name;

    @Field
    private String description;

    @Field
    private String photo;

    @Field
    private String address;

    @Field
    private Double latitude;

    @Field
    private Double longitude;

    @Field
    private String phone;

    @Field
    private List<OperatingHours> operatingHours = new ArrayList<>();

    @Field
    private boolean open = true;

    @Field
    private double averageRating = 0.0;

    @Field
    private List<MenuItem> menu = new ArrayList<>();

    @Field
    private Instant createdAt = Instant.now();
}
