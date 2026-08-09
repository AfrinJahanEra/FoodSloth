package com.foodsloth.frontend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * frontend-service - the FoodSloth web UI.
 *
 * Structure note: port 9008 | no database | no RabbitMQ | not registered in Eureka
 *
 * This service does exactly one thing: serve the single-page app from
 * src/main/resources/static. All data flows from the browser straight to api-gateway
 * (http://localhost:8080) with the user's JWT, so this module carries no backend
 * dependencies and nothing here ever talks to another service server-side.
 *
 * The gateway allows this origin via its CORS configuration (see api-gateway CorsConfig).
 */
@SpringBootApplication
public class FrontendServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FrontendServiceApplication.class, args);
    }

}
