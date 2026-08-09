package com.service.cart_service.dto;

/** Body of {@code POST /carts/me/checkout}. Where the food goes and how it is paid for. */
public record CheckoutRequest(
        String deliveryAddress,
        Double deliveryLatitude,
        Double deliveryLongitude,
        /** {@code CASH_ON_DELIVERY} or card. */
        String paymentMethod,
        String note
) {
}
