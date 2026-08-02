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
 *   org.sda.notificationservice.controller  - REST endpoints
 *   org.sda.notificationservice.entity      - MongoDB documents
 *   org.sda.notificationservice.repository  - data access
 *   org.sda.notificationservice.service     - business logic
 *
 * Start order: service-registry (8761) first, then this service, then api-gateway (8080).
 */
@SpringBootApplication
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }

}
