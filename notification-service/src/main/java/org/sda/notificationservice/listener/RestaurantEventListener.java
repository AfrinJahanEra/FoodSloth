package org.sda.notificationservice.listener;

import org.sda.notificationservice.dto.event.OrderRejectedEvent;
import org.sda.notificationservice.dto.event.OrderStatusEvent;
import org.sda.notificationservice.messaging.Constants;
import org.sda.notificationservice.service.NotificationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Restaurant Service events - what the kitchen is doing with the order. */
@Component
public class RestaurantEventListener {

    private final NotificationService notificationService;

    public RestaurantEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = Constants.QUEUE_ORDER_ACCEPTED)
    public void onOrderAccepted(OrderStatusEvent event) {
        notificationService.onOrderAccepted(event);
    }

    @RabbitListener(queues = Constants.QUEUE_ORDER_REJECTED)
    public void onOrderRejected(OrderRejectedEvent event) {
        notificationService.onOrderRejected(event);
    }

    @RabbitListener(queues = Constants.QUEUE_ORDER_READY)
    public void onOrderReady(OrderStatusEvent event) {
        notificationService.onOrderReady(event);
    }
}
