package org.sda.orderservice.dto;

import org.sda.orderservice.entity.DeliveryType;
import org.sda.orderservice.entity.PaymentMethod;

import java.util.List;

// TODO: once Cart Service exists, this may instead be built server-side from the user's cart
// rather than accepting items directly from the client.
// userId is deliberately not a field here: it is derived from the authenticated JWT rather
// than trusted from the request body, so a client can't place orders on another user's behalf.
public record CreateOrderRequest(
        String restaurantId,
        String deliveryAddressId,
        DeliveryType deliveryType,
        PaymentMethod paymentMethod,
        List<OrderItemRequest> items
) {
}
