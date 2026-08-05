package org.sda.userservice.entity;

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

@Document(collection = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    private String id;

    @Field
    @Indexed(unique = true, sparse = true)
    private String email;

    @Field
    @Indexed(unique = true, sparse = true)
    private String phone;

    @Field
    private String password;

    @Field
    private String name;

    @Field
    private String photo;

    @Field
    private Role role = Role.CUSTOMER;

    @Field
    private String vehicleType;

    @Field
    private String licenseNumber;

    @Field
    private String restaurantId;

    @Field
    private List<Address> addresses = new ArrayList<>();

    @Field
    private List<String> foodPreferences = new ArrayList<>();

    @Field
    private List<String> dietaryTags = new ArrayList<>();

    @Field
    private String defaultPaymentMethod;

    @Field
    private Instant createdAt = Instant.now();
}
