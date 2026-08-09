package org.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Consumed from {@code order.cancelled} - refund it if it was already charged. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderCancelledEvent(String orderId) {
}
