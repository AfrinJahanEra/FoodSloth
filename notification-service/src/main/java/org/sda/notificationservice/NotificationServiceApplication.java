package org.sda.notificationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * notification-service - Push, email and SMS notifications.
 *
 * Structure note: port 9007 | MongoDB database food_notification_db
 * Reached through api-gateway at /notifications/**
 *
 * Package layout:
 *   org.sda.notificationservice.listener  - RabbitMQ event consumers
 *   org.sda.notificationservice.config    - RabbitMQ topology configuration and constants
 *   org.sda.notificationservice.service   - notification formatting/dispatch
 *   org.sda.notificationservice.dto.event - inbound event payloads
 *
 * No REST endpoints; this service only consumes RabbitMQ events (order.confirmed,
 * order.cancelled, order.delivered) and logs a formatted notification message.
 *
 * Start order: service-registry (8761) first, then this service, then api-gateway (8080).
 */
@SpringBootApplication
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }

}
