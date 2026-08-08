package com.service.cart_service.event;

import com.service.cart_service.entity.CartItem;

import java.time.Instant;
import java.util.List;

/**
 * Published to the existing RabbitMQ exchange/routing key (see Constants and
 * MessagingConfig) when a cart is checked out. Order Service is expected to
 * consume this to create the order - Cart Service itself has no listener,
 * since it doesn't need to react to anything downstream.
 */
public record CartCheckedOutEvent(
        String eventType,
        String cartId,
        String userId,
        List<CartItem> items,
        String couponCode,
        double subtotal,
        double tax,
        double deliveryFee,
        double total,
        Instant checkedOutAt
) {
}
