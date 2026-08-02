package org.sda.restaurantservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * restaurant-service - Restaurant profiles, menus and item availability.
 *
 * Structure note: port 9002 | MongoDB database food_restaurant_db
 * Reached through api-gateway at /restaurants/**
 *
 * Package layout:
 *   org.sda.restaurantservice.controller  - REST endpoints
 *   org.sda.restaurantservice.entity      - MongoDB documents
 *   org.sda.restaurantservice.repository  - data access
 *   org.sda.restaurantservice.service     - business logic
 *
 * Start order: service-registry (8761) first, then this service, then api-gateway (8080).
 */
@SpringBootApplication
public class RestaurantServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RestaurantServiceApplication.class, args);
    }

}
