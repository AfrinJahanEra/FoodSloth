package org.sda.orderservice.dto.event;

// TODO: consumed from Payment Service (not implemented in this scope). Adjust fields once that
// service's actual event contract is finalized.
public record PaymentSucceededEvent(
        String orderId,
        String userId,
        String transactionId
) {
}
