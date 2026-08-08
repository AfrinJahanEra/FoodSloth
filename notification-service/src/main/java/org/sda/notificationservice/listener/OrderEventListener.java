package org.sda.notificationservice.listener;

import org.sda.notificationservice.config.Constants;
import org.sda.notificationservice.dto.event.OrderCancelledEvent;
import org.sda.notificationservice.dto.event.OrderConfirmedEvent;
import org.sda.notificationservice.dto.event.OrderDeliveredEvent;
import org.sda.notificationservice.service.NotificationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    private final NotificationService notificationService;

    public OrderEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = Constants.QUEUE_ORDER_CONFIRMED)
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        notificationService.notifyOrderConfirmed(event);
    }

    @RabbitListener(queues = Constants.QUEUE_ORDER_CANCELLED)
    public void onOrderCancelled(OrderCancelledEvent event) {
        notificationService.notifyOrderCancelled(event);
    }

    @RabbitListener(queues = Constants.QUEUE_ORDER_DELIVERED)
    public void onOrderDelivered(OrderDeliveredEvent event) {
        notificationService.notifyOrderDelivered(event);
    }
}
