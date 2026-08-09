package org.sda.orderservice.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Consumed from {@code payment.failed}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentFailedEvent(
        String orderId,
        String userId,
        String paymentId,
        String reason
) {
}
