package org.sda.cartservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * cart-service - Per-user cart, line items and totals.
 *
 * Structure note: port 9003 | MongoDB database food_cart_db
 * Reached through api-gateway at /carts/**
 *
 * Package layout:
 *   org.sda.cartservice.controller  - REST endpoints
 *   org.sda.cartservice.entity      - MongoDB documents
 *   org.sda.cartservice.repository  - data access
 *   org.sda.cartservice.service     - business logic
 *
 * Start order: service-registry (8761) first, then this service, then api-gateway (8080).
 */
@SpringBootApplication
public class CartServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CartServiceApplication.class, args);
    }

}
