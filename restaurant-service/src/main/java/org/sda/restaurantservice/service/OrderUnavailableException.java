package org.sda.restaurantservice.service;

/**
 * Thrown when a checkout cannot be priced - an empty cart, a closed restaurant, or an item that is
 * gone or sold out.
 *
 * <p>Deliberately not a {@code ResponseStatusException}: nothing here is reached over HTTP. The
 * listener catches this and publishes {@code restaurant.order-unavailable} so the customer is told,
 * rather than letting the message fail and be retried three times over something a retry cannot fix.
 */
public class OrderUnavailableException extends RuntimeException {

    public OrderUnavailableException(String reason) {
        super(reason);
    }
}
