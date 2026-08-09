package org.sda.deliveryservice.listener;

import org.sda.deliveryservice.dto.event.OrderCancelledEvent;
import org.sda.deliveryservice.dto.event.OrderReadyEvent;
import org.sda.deliveryservice.messaging.Constants;
import org.sda.deliveryservice.service.DeliveryService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * The only way work enters this service.
 *
 * <p>Nothing here calls another service over HTTP: the events carry every coordinate and id that
 * the delivery needs, so Delivery Service can staff and track a ride with every other service
 * down. If this service is the one that is down, the messages simply wait in its queues.
 */
@Component
public class OrderEventListener {

    private final DeliveryService deliveryService;

    public OrderEventListener(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @RabbitListener(queues = Constants.QUEUE_ORDER_READY)
    public void onOrderReady(OrderReadyEvent event) {
        deliveryService.onOrderReady(event);
    }

    @RabbitListener(queues = Constants.QUEUE_ORDER_CANCELLED)
    public void onOrderCancelled(OrderCancelledEvent event) {
        deliveryService.onOrderCancelled(event.orderId());
    }
}
