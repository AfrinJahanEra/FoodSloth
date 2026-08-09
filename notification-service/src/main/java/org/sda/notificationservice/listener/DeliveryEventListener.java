package org.sda.notificationservice.listener;

import org.sda.notificationservice.dto.event.DeliveryEvent;
import org.sda.notificationservice.messaging.Constants;
import org.sda.notificationservice.service.NotificationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Delivery Service events, including the rider-arrival alert. */
@Component
public class DeliveryEventListener {

    private final NotificationService notificationService;

    public DeliveryEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = Constants.QUEUE_DELIVERY_ASSIGNED)
    public void onRiderAssigned(DeliveryEvent event) {
        notificationService.onRiderAssigned(event);
    }

    @RabbitListener(queues = Constants.QUEUE_DELIVERY_STARTED)
    public void onOutForDelivery(DeliveryEvent event) {
        notificationService.onOutForDelivery(event);
    }

    @RabbitListener(queues = Constants.QUEUE_DELIVERY_ARRIVING)
    public void onRiderArriving(DeliveryEvent event) {
        notificationService.onRiderArriving(event);
    }
}
