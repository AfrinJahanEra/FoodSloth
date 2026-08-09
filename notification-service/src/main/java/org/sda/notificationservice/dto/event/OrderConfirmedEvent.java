package org.sda.notificationservice.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Consumed from {@code order.confirmed} - payment cleared and the order is going to the kitchen.
 * Triggers the order-confirmation email as well as a push.
 *
 * <p>Every record in this package is a deliberate subset of what the publisher sends: Notification
 * Service reads the fields it needs to word a message and ignores the rest, so no event JAR has to
 * be shared and publishers can extend their payloads freely.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderConfirmedEvent(
        String orderId,
        Long orderNo,
        String userId,
        String restaurantId,
        Double grandTotal
) {
}
