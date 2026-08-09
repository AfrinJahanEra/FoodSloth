package org.sda.orderservice.dto.event;

import java.util.List;

/**
 * Published as {@code order.reorder-requested} when a customer asks to order a past order again.
 *
 * <p>It deliberately has the same shape as Cart Service's {@code cart.checked-out}: a reorder is
 * just "price these items again", so Restaurant Service handles both on its single intake queue.
 * The fresh orderId is minted here before publishing and rides through every later event.
 */
public record ReorderRequestedEvent(
        String orderId,
        String userId,
        List<ReorderItem> items,
        String deliveryAddress,
        Double deliveryLatitude,
        Double deliveryLongitude,
        String paymentMethod,
        String note
) {

    public record ReorderItem(String itemId, Integer quantity) {
    }
}
