package org.sda.orderservice.listener;

import org.sda.orderservice.dto.event.OrderAcceptedEvent;
import org.sda.orderservice.messaging.Constants;
import org.sda.orderservice.service.OrderService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

// TODO: publisher side (Restaurant Service) is not implemented in this scope.
@Component
public class RestaurantEventListener {

    private final OrderService orderService;

    public RestaurantEventListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @RabbitListener(queues = Constants.QUEUE_ORDER_ACCEPTED)
    public void onOrderAccepted(OrderAcceptedEvent event) {
        orderService.markPreparing(event.orderId());
    }
}
