package org.sda.restaurantservice.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Consumed from {@code order.confirmed} - payment cleared, so the ticket that has been waiting may
 * now appear on the kitchen screen.
 *
 * <p>Only the order id is needed: this service priced the order itself, so it already holds the
 * items. Reading more than that would be replicating Order Service's data.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderConfirmedEvent(String orderId) {
}
