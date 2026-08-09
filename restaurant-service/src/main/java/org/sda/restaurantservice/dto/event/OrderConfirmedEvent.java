package org.sda.restaurantservice.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Consumed from {@code order.confirmed} - payment cleared, so the ticket that has been waiting may
 * now appear on the kitchen screen.
 *
 * <p>{@code orderNo} is the sequential number Order Service minted for the order; the kitchen
 * stores it so its screen and the ready event can show a friendly #number instead of the UUID.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderConfirmedEvent(String orderId, Long orderNo) {
}
