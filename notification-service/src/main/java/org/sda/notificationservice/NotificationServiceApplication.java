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
 *   org.sda.notificationservice.controller - REST endpoints for the client (devices, contact,
 *                                           preferences, in-app inbox, marketing broadcast)
 *   org.sda.notificationservice.listener   - RabbitMQ event consumers (order, payment,
 *                                           restaurant, delivery, marketing)
 *   org.sda.notificationservice.publisher  - publishes marketing.broadcast
 *   org.sda.notificationservice.messaging  - exchange, routing key and queue names
 *   org.sda.notificationservice.config     - RabbitMQ topology configuration
 *   org.sda.notificationservice.service    - message wording, dispatch and the inbox
 *   org.sda.notificationservice.channel    - push / email / SMS senders
 *   org.sda.notificationservice.entity     - Recipient (where to reach a user) and
 *                                           Notification (what was sent, and whether it worked)
 *   org.sda.notificationservice.repository - MongoDB repositories
 *   org.sda.notificationservice.dto        - request bodies and inbound event payloads
 *
 * This service never calls another service. It learns everything it needs from events on
 * food.exchange, and the client registers its own device token, email and phone over REST.
 *
 * Start order: service-registry (8761) first, then this service, then api-gateway (8080).
 */
@SpringBootApplication
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }

}
