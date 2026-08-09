package org.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Consumed from {@code order.payment-requested} - an order is waiting to be paid for.
 *
 * <p>{@code amount} is in major units (taka), which is how the rest of the platform talks about
 * money. Payment Service converts it to the smallest unit exactly once, where Stripe needs it.
 *
 * <p>{@code paymentMethod} is {@code CASH_ON_DELIVERY} or anything else (treated as card).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentRequestedEvent(
        String orderId,
        String userId,
        Double amount,
        String currency,
        String paymentMethod,
        String description
) {
}
