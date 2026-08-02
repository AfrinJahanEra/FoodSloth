package org.sda.userservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * user-service - Accounts, authentication, addresses and profiles.
 *
 * Structure note: port 9001 | MongoDB database food_user_db
 * Reached through api-gateway at /users/**
 *
 * Package layout:
 *   org.sda.userservice.controller  - REST endpoints
 *   org.sda.userservice.entity      - MongoDB documents
 *   org.sda.userservice.repository  - data access
 *   org.sda.userservice.service     - business logic
 *
 * Start order: service-registry (8761) first, then this service, then api-gateway (8080).
 */
@SpringBootApplication
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }

}
