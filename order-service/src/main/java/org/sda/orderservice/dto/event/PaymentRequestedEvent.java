package org.sda.orderservice.dto.event;

import org.sda.orderservice.entity.PaymentMethod;

import java.math.BigDecimal;

// Published by Order Service right after an order is created.
public record PaymentRequestedEvent(
        String orderId,
        String userId,
        BigDecimal amount,
        PaymentMethod paymentMethod
) {
}
