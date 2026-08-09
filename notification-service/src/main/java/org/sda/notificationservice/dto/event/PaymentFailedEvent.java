package org.sda.notificationservice.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Consumed from {@code payment.failed}. Time-critical, so it goes out by SMS as well as push. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentFailedEvent(
        String orderId,
        String userId,
        String paymentId,
        String reason
) {
}
