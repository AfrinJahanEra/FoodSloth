package org.sda.restaurantservice.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Consumed from {@code order.cancelled} - stop cooking it. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderCancelledEvent(String orderId) {
}
