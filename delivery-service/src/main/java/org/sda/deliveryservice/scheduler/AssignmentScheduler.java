package org.sda.deliveryservice.scheduler;

import org.sda.deliveryservice.service.DeliveryService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Retries the deliveries that found no rider when they arrived.
 *
 * <p>An order-ready event that cannot be staffed immediately must not be lost, and re-queueing the
 * message would only spin the broker. Instead the delivery is parked as
 * {@code PENDING_ASSIGNMENT} and this sweep picks it up as soon as a rider comes online.
 */
@Component
public class AssignmentScheduler {

    private final DeliveryService deliveryService;

    public AssignmentScheduler(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @Scheduled(fixedDelayString = "${delivery.assignment-sweep-ms}")
    public void sweep() {
        deliveryService.sweepUnassigned();
    }
}
