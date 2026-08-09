package org.sda.deliveryservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * delivery-service - Rider assignment and delivery tracking.
 *
 * Structure note: port 9006 | MongoDB database food_delivery_db
 * Reached through api-gateway at /deliveries/**
 *
 * Package layout:
 *   org.sda.deliveryservice.controller  - REST endpoints (rider app + customer tracking)
 *   org.sda.deliveryservice.listener    - inbound RabbitMQ consumers
 *   org.sda.deliveryservice.publisher   - outbound RabbitMQ events
 *   org.sda.deliveryservice.messaging   - exchange, routing key and queue names
 *   org.sda.deliveryservice.scheduler   - retry sweep for unstaffed deliveries
 *   org.sda.deliveryservice.entity      - MongoDB documents
 *   org.sda.deliveryservice.repository  - data access
 *   org.sda.deliveryservice.service     - business logic
 *
 * Talks to other services only through food.exchange - never over REST.
 *
 * Start order: service-registry (8761) first, then this service, then api-gateway (8080).
 * RabbitMQ and MongoDB must be reachable; no other business service needs to be up.
 */
@SpringBootApplication
@EnableScheduling
public class DeliveryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeliveryServiceApplication.class, args);
    }

}
