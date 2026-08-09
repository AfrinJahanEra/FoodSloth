package org.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Consumed from {@code delivery.completed}. A cash-on-delivery payment is only confirmed
 * once the rider has handed the order over - this event is the trigger.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeliveryCompletedEvent(
        String orderId,
        Long orderNo
) {
}
