package org;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * payment-service - Payment capture, refunds and transaction records.
 *
 * Structure note: port 9005 | MongoDB database food_payment_db
 * Reached through api-gateway at /payments/**
 *
 * Package layout:
 *   org.controller  - REST endpoints
 *   org.entity      - MongoDB documents
 *   org.repository  - data access
 *   org.service     - business logic
 *   org.config      - Stripe/infra configuration
 *   org.client      - outbound REST calls to other services (order-service)
 *
 * This class sits directly in the "org" package (no scan-worthy code lives there itself),
 * so component/repository scanning is pinned explicitly to the subpackages above instead of
 * defaulting to "org" and below - a default scan from "org" would sweep in every third-party
 * class on the classpath whose package also starts with "org" (org.springframework, etc.).
 *
 * Start order: service-registry (8761) first, then this service, then api-gateway (8080).
 */
@SpringBootApplication
@ComponentScan(basePackages = {"org.controller", "org.entity", "org.service", "org.repository", "org.config", "org.client"})
@EnableMongoRepositories(basePackages = "org.repository")
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }

}
