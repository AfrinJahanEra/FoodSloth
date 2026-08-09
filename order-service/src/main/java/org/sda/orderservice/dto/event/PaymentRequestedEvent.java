package org.sda.orderservice.dto.event;

/**
 * Published as {@code order.payment-requested} right after the order is created. Payment Service
 * then opens a Stripe session (or accepts cash on delivery) and answers with payment.succeeded /
 * payment.failed.
 *
 * <p>{@code amount} is in major units (taka) - Payment Service does the one conversion to the
 * smallest unit that Stripe charges in.
 */
public record PaymentRequestedEvent(
        String orderId,
        String userId,
        Double amount,
        String currency,
        String paymentMethod,
        String description
) {
}
