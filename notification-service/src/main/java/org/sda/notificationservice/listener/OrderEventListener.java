package org.sda.notificationservice.listener;

import org.sda.notificationservice.dto.event.OrderCancelledEvent;
import org.sda.notificationservice.dto.event.OrderConfirmedEvent;
import org.sda.notificationservice.dto.event.OrderStatusEvent;
import org.sda.notificationservice.messaging.Constants;
import org.sda.notificationservice.service.NotificationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Order Service events. */
@Component
public class OrderEventListener {

    private final NotificationService notificationService;

    public OrderEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = Constants.QUEUE_ORDER_CONFIRMED)
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        notificationService.onOrderConfirmed(event);
    }

    @RabbitListener(queues = Constants.QUEUE_ORDER_CANCELLED)
    public void onOrderCancelled(OrderCancelledEvent event) {
        notificationService.onOrderCancelled(event);
    }

    @RabbitListener(queues = Constants.QUEUE_ORDER_DELIVERED)
    public void onOrderDelivered(OrderStatusEvent event) {
        notificationService.onOrderDelivered(event);
    }
}
