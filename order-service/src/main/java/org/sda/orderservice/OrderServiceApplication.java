package org.sda.orderservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * order-service - Order lifecycle: the priced checkout becomes an order, and every later status
 * change arrives as an event.
 *
 * Structure note: port 9004 | MongoDB database food_order_db
 * Reached through api-gateway at /orders/**
 *
 * Package layout:
 *   org.sda.orderservice.controller  - REST endpoints (read, cancel, reorder)
 *   org.sda.orderservice.entity      - MongoDB documents
 *   org.sda.orderservice.repository  - data access
 *   org.sda.orderservice.service     - business logic
 *   org.sda.orderservice.messaging   - exchange, queue and routing-key names
 *   org.sda.orderservice.config      - RabbitMQ topology
 *   org.sda.orderservice.listener    - RabbitMQ event consumers
 *   org.sda.orderservice.publisher   - RabbitMQ event producers
 *   org.sda.orderservice.dto         - responses and inbound/outbound event payloads
 *
 * There is no endpoint to create an order: it is created by restaurant.order-priced, and this
 * service calls no other service - identity comes from the gateway's X-User-Id/X-User-Role
 * headers and everything else comes in on food.exchange.
 */
@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }

}
