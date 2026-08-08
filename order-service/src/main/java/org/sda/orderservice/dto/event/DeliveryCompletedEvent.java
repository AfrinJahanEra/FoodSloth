package org.sda.orderservice.dto.event;

// TODO: consumed from Delivery Service (not implemented in this scope). Adjust fields once that
// service's actual event contract is finalized.
public record DeliveryCompletedEvent(
        String orderId
) {
}
