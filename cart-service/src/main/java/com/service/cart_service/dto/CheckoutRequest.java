package com.service.cart_service.dto;

/** Body of {@code POST /carts/me/checkout}. Where the food goes and how it is paid for. */
public record CheckoutRequest(
        String deliveryAddress,
        Double deliveryLatitude,
        Double deliveryLongitude,
        /** The number the rider can call at the door; rides inside the checkout event. */
        String contactPhone,
        /** {@code CASH_ON_DELIVERY} or card. */
        String paymentMethod,
        String note
) {
}
