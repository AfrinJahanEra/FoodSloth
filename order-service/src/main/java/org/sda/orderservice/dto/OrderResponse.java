package org.sda.orderservice.dto;

import org.sda.orderservice.entity.OrderItem;
import org.sda.orderservice.entity.OrderStatus;
import org.sda.orderservice.entity.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** What the client sees when it polls {@code GET /orders/{orderId}}. */
public record OrderResponse(
        String id,
        Long orderNo,
        String userId,
        String restaurantId,
        String restaurantName,
        List<OrderItem> items,
        BigDecimal subtotal,
        BigDecimal deliveryCharge,
        BigDecimal tax,
        BigDecimal grandTotal,
        String currency,
        PaymentMethod paymentMethod,
        String deliveryAddress,
        Double deliveryLatitude,
        Double deliveryLongitude,
        String note,
        OrderStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
