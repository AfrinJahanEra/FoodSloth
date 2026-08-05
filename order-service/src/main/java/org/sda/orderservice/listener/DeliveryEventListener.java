package org.sda.orderservice.listener;

import org.sda.orderservice.dto.event.DeliveryCompletedEvent;
import org.sda.orderservice.dto.event.DeliveryStartedEvent;
import org.sda.orderservice.messaging.Constants;
import org.sda.orderservice.service.OrderService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

// TODO: publisher side (Delivery Service) is not implemented in this scope.
@Component
public class DeliveryEventListener {

    private final OrderService orderService;

    public DeliveryEventListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @RabbitListener(queues = Constants.QUEUE_DELIVERY_STARTED)
    public void onDeliveryStarted(DeliveryStartedEvent event) {
        orderService.markOutForDelivery(event.orderId());
    }

    @RabbitListener(queues = Constants.QUEUE_DELIVERY_COMPLETED)
    public void onDeliveryCompleted(DeliveryCompletedEvent event) {
        orderService.markDelivered(event.orderId());
    }
}
