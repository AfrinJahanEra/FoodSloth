package org.sda.deliveryservice.dto.event;

import java.time.Instant;

/** Published on {@code delivery.completed} when the rider hands the order over. */
public record DeliveryCompletedEvent(
        String orderId,
        /** Sequential human-facing order number (#123) forwarded for completeness. */
        Long orderNo,
        String userId,
        String deliveryId,
        String riderId,
        Instant deliveredAt
) {
}
