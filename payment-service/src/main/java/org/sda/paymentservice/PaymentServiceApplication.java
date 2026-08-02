package org.sda.paymentservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * payment-service - Payment capture, refunds and transaction records.
 *
 * Structure note: port 9005 | MongoDB database food_payment_db
 * Reached through api-gateway at /payments/**
 *
 * Package layout:
 *   org.sda.paymentservice.controller  - REST endpoints
 *   org.sda.paymentservice.entity      - MongoDB documents
 *   org.sda.paymentservice.repository  - data access
 *   org.sda.paymentservice.service     - business logic
 *
 * Start order: service-registry (8761) first, then this service, then api-gateway (8080).
 */
@SpringBootApplication
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }

}
