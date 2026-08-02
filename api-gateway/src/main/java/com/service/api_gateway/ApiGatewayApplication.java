package com.service.api_gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * api-gateway - Spring Cloud Gateway (WebFlux). Single entry point for all clients.
 *
 * Structure note: port 8080 | no database | routes defined in application.yml
 * under spring.cloud.gateway.server.webflux.routes
 *
 * Routes:
 *   /users/**          -> lb://USER-SERVICE          (9001)
 *   /restaurants/**    -> lb://RESTAURANT-SERVICE    (9002)
 *   /carts/**          -> lb://CART-SERVICE          (9003)
 *   /orders/**         -> lb://ORDER-SERVICE         (9004)
 *   /payments/**       -> lb://PAYMENT-SERVICE       (9005)
 *   /deliveries/**     -> lb://DELIVERY-SERVICE      (9006)
 *   /notifications/**  -> lb://NOTIFICATION-SERVICE  (9007)
 *
 * Start order: start this last, so the services are already registered in Eureka.
 */
@SpringBootApplication
public class ApiGatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApiGatewayApplication.class, args);
	}

}
