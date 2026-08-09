package org.sda.notificationservice.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Consumed from {@code delivery.assigned}, {@code delivery.started} and {@code delivery.arriving}.
 *
 * <p>One record for all three because Delivery Service publishes the same shape each time; the
 * fields a given event does not set simply arrive as null. {@code riderDisplayName} and
 * {@code etaMinutes} are what make these messages useful to the customer.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeliveryEvent(
        String orderId,
        String userId,
        String deliveryId,
        String riderId,
        String riderDisplayName,
        String riderPhone,
        Integer etaMinutes
) {
}
