package org.sda.deliveryservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * delivery-service - Rider assignment and delivery tracking.
 *
 * Structure note: port 9006 | MongoDB database food_delivery_db
 * Reached through api-gateway at /deliveries/**
 *
 * Package layout:
 *   org.sda.deliveryservice.controller  - REST endpoints
 *   org.sda.deliveryservice.entity      - MongoDB documents
 *   org.sda.deliveryservice.repository  - data access
 *   org.sda.deliveryservice.service     - business logic
 *
 * Start order: service-registry (8761) first, then this service, then api-gateway (8080).
 */
@SpringBootApplication
public class DeliveryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeliveryServiceApplication.class, args);
    }

}
