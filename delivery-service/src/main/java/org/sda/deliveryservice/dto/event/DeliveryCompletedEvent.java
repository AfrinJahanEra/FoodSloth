package org.sda.deliveryservice.dto.event;

import java.time.Instant;

/** Published on {@code delivery.completed} when the rider hands the order over. */
public record DeliveryCompletedEvent(
        String orderId,
        String userId,
        String deliveryId,
        String riderId,
        Instant deliveredAt
) {
}
