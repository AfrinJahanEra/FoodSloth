package org.sda.notificationservice.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Consumed from {@code payment.succeeded} - the trigger for the payment receipt.
 *
 * <p>{@code amountMinor} is in the currency's smallest unit (poisha for BDT), which is how Payment
 * Service stores and charges it. Formatting it for a human is done here.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentSucceededEvent(
        String orderId,
        Long orderNo,
        String userId,
        String paymentId,
        Long amountMinor,
        String currency,
        String paymentMethod
) {
}
