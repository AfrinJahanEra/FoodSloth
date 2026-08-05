package org.sda.orderservice.dto;

import org.sda.orderservice.entity.DeliveryType;
import org.sda.orderservice.entity.OrderItem;
import org.sda.orderservice.entity.OrderStatus;
import org.sda.orderservice.entity.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        String id,
        String userId,
        String restaurantId,
        String deliveryAddressId,
        DeliveryType deliveryType,
        PaymentMethod paymentMethod,
        List<OrderItem> items,
        BigDecimal subtotal,
        BigDecimal deliveryCharge,
        BigDecimal tax,
        BigDecimal grandTotal,
        OrderStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
