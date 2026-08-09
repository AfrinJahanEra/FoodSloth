package org.sda.restaurantservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * restaurant-service - Restaurant profile, menu, order pricing and the kitchen queue.
 *
 * Structure note: port 9002 | MongoDB database food_restaurant_db
 * Reached through api-gateway at /restaurant/**
 *
 * Package layout:
 *   org.sda.restaurantservice.controller - REST endpoints (profile + menu, and the kitchen screen)
 *   org.sda.restaurantservice.listener   - RabbitMQ event consumers (checkout intake, confirmed,
 *                                          cancelled)
 *   org.sda.restaurantservice.publisher  - publishes the priced/accepted/rejected/ready events
 *   org.sda.restaurantservice.messaging  - exchange, routing key and queue names
 *   org.sda.restaurantservice.config     - RabbitMQ topology configuration
 *   org.sda.restaurantservice.entity     - MongoDB documents
 *   org.sda.restaurantservice.repository - data access
 *   org.sda.restaurantservice.service    - pricing rules and the kitchen order lifecycle
 *   org.sda.restaurantservice.dto.event  - event payloads in and out
 *
 * This service is the only place a price is decided, and it calls no other service: work arrives
 * as events on food.exchange and progress goes back out the same way.
 *
 * Start order: service-registry (8761) first, then this service, then api-gateway (8080).
 */
@SpringBootApplication
public class RestaurantServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(RestaurantServiceApplication.class, args);
	}

}
