package org.sda.orderservice.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Consumed from {@code delivery.completed} - the customer has the food. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeliveryCompletedEvent(String orderId) {
}
