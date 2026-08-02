package org.sda.orderservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * order-service - Checkout, order lifecycle and status timeline.
 *
 * Structure note: port 9004 | MongoDB database food_order_db
 * Reached through api-gateway at /orders/**
 *
 * Package layout:
 *   org.sda.orderservice.controller  - REST endpoints
 *   org.sda.orderservice.entity      - MongoDB documents
 *   org.sda.orderservice.repository  - data access
 *   org.sda.orderservice.service     - business logic
 *
 * Start order: service-registry (8761) first, then this service, then api-gateway (8080).
 */
@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }

}
