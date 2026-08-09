package org.sda.deliveryservice.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Consumed from {@code order.cancelled}.
 *
 * <p>A deliberate subset of what Order Service publishes - this service only needs to know which
 * delivery to stop. Unknown fields are ignored so the publisher can add to its payload without
 * breaking this consumer, which is the point of not sharing an event JAR.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderCancelledEvent(
        String orderId
) {
}
