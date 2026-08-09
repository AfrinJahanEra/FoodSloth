package org.dto;

import org.entity.Payment;
import org.entity.PaymentStatus;

import java.time.Instant;

/**
 * A payment as clients see it: enough to show status, amount and the Stripe link. The Stripe
 * session and payment-intent ids are internal handles and never leave this service.
 */
public record PaymentResponse(
        String id,
        String orderId,
        Long orderNo,
        String userId,
        Long amount,
        String currency,
        PaymentStatus status,
        String paymentMethod,
        String checkoutUrl,
        String failureReason,
        Instant createdAt,
        Instant updatedAt) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getOrderId(), payment.getOrderNo(), payment.getUserId(), payment.getAmount(),
                payment.getCurrency(), payment.getStatus(), payment.getPaymentMethod(), payment.getCheckoutUrl(),
                payment.getFailureReason(), payment.getCreatedAt(), payment.getUpdatedAt());
    }
}
