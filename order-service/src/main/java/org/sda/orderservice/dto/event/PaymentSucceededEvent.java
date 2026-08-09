package org.sda.orderservice.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Consumed from {@code payment.succeeded}. Order Service only acts on the order id - the amounts
 * and method are already recorded on the order itself.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentSucceededEvent(
        String orderId,
        String userId,
        String paymentId
) {
}
