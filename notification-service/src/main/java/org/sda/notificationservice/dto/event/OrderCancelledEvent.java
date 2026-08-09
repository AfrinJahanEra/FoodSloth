package org.sda.notificationservice.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Consumed from {@code order.cancelled}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderCancelledEvent(
        String orderId,
        Long orderNo,
        String userId,
        String restaurantId,
        String reason
) {
}
