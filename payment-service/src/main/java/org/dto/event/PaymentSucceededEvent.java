package org.dto.event;

/**
 * Published as {@code payment.succeeded} - the money is secured, either captured on a card or
 * accepted as cash on delivery.
 *
 * <p>{@code amountMinor} is in the currency's smallest unit (poisha for BDT), which is what was
 * actually charged. Order Service moves the order to CONFIRMED and Notification Service turns this
 * into the customer's receipt.
 */
public record PaymentSucceededEvent(
        String orderId,
        String userId,
        String paymentId,
        Long amountMinor,
        String currency,
        String paymentMethod
) {
}
