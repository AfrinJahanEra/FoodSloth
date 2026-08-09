package com.service.cart_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * cart-service - One shopping session per user; checkout starts the order pipeline.
 *
 * Structure note: port 9003 | MongoDB database food_cart_db
 * Reached through api-gateway at /carts/**
 *
 * Package layout:
 *   com.service.cart_service.controller  - REST endpoints
 *   com.service.cart_service.service     - cart logic and checkout
 *   com.service.cart_service.entity      - MongoDB documents
 *   com.service.cart_service.repository  - data access
 *   com.service.cart_service.messaging   - exchange and routing key names
 *   com.service.cart_service.config      - RabbitMQ topology configuration
 *   com.service.cart_service.dto         - request bodies and outbound event payloads
 *
 * The cart stores nothing but item ids and quantities - prices belong to Restaurant Service.
 * Checkout mints the orderId, publishes cart.checked-out and returns 202 immediately; this service
 * calls no other service and consumes nothing.
 *
 * Start order: service-registry (8761) first, then this service, then api-gateway (8080).
 */
@SpringBootApplication
public class CartServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(CartServiceApplication.class, args);
	}

}
