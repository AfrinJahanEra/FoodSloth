package org.sda.orderservice.dto.event;

/** Published as {@code order.delivered} - the final stop of the order pipeline. */
public record OrderDeliveredEvent(
        String orderId,
        String userId,
        String restaurantId
) {
}
