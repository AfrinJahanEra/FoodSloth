package com.service.service_registry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * service-registry - Netflix Eureka server. Every other module registers here.
 *
 * Structure note: port 8761 | no database | not routed through the gateway
 * Dashboard: http://localhost:8761
 *
 * Start order: this module must be running before any other, otherwise the
 * services log "Connection refused" while they retry registration.
 */
@SpringBootApplication
@EnableEurekaServer
public class ServiceRegistryApplication {

	public static void main(String[] args) {
		SpringApplication.run(ServiceRegistryApplication.class, args);
	}

}
