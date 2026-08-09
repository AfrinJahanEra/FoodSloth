package org.dto.event;

/**
 * Published as {@code payment.failed} - declined, expired, or cancelled by the customer.
 *
 * <p>Order Service moves the order to PAYMENT_FAILED and Notification Service tells the customer,
 * who can then retry from the order page.
 */
public record PaymentFailedEvent(
        String orderId,
        /** Sequential human-facing order number (#123) for the failure message. */
        Long orderNo,
        String userId,
        String paymentId,
        String reason
) {
}
