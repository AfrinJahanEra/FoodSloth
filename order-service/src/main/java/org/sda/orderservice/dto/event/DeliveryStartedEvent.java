package org.sda.orderservice.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Consumed from {@code delivery.started} - the rider has the food. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeliveryStartedEvent(String orderId) {
}
