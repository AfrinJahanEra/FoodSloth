package com.service.cart_service.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Thin REST client to Restaurant Service. Cart Service treats it as the
 * single source of truth for menu item name/price/availability - client
 * requests are never trusted for those fields, and Cart Service keeps no
 * local copy of the menu.
 * <p>
 * The target instance is resolved from the existing Eureka registry via
 * DiscoveryClient - a direct service-to-service call that bypasses the API
 * Gateway - rather than a hardcoded host:port.
 */
@Component
public class RestaurantClient {

    private static final String SERVICE_ID = "restaurant-service";

    @Autowired
    private DiscoveryClient discoveryClient;

    private final RestClient restClient = RestClient.create();

    public MenuItemResponse getMenuItem(String itemId) {
        String baseUrl = resolveBaseUrl();
        try {
            MenuItemResponse item = restClient.get()
                    .uri(baseUrl + "/restaurant/menu/{itemId}", itemId)
                    .retrieve()
                    .body(MenuItemResponse.class);

            if (item == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Menu item not found: " + itemId);
            }
            return item;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Menu item not found: " + itemId);
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Restaurant Service returned an error while looking up item " + itemId);
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Restaurant Service is unavailable");
        }
    }

    private String resolveBaseUrl() {
        List<ServiceInstance> instances = discoveryClient.getInstances(SERVICE_ID);
        if (instances.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No instances of restaurant-service are registered with Eureka");
        }
        return instances.get(0).getUri().toString();
    }
}
