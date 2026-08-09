package org.sda.orderservice.listener;

import org.sda.orderservice.dto.event.KitchenDecisionEvent;
import org.sda.orderservice.dto.event.OrderPricedEvent;
import org.sda.orderservice.dto.event.OrderUnavailableEvent;
import org.sda.orderservice.messaging.Constants;
import org.sda.orderservice.service.OrderService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Restaurant Service events. This is where orders come from - the pricing result creates them, and
 * the kitchen's decisions move them along.
 */
@Component
public class RestaurantEventListener {

    private final OrderService orderService;

    public RestaurantEventListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @RabbitListener(queues = Constants.QUEUE_ORDER_PRICED)
    public void onOrderPriced(OrderPricedEvent event) {
        orderService.onOrderPriced(event);
    }

    @RabbitListener(queues = Constants.QUEUE_ORDER_UNAVAILABLE)
    public void onOrderUnavailable(OrderUnavailableEvent event) {
        orderService.onOrderUnavailable(event);
    }

    @RabbitListener(queues = Constants.QUEUE_ORDER_ACCEPTED)
    public void onOrderAccepted(KitchenDecisionEvent event) {
        orderService.markPreparing(event.orderId());
    }

    @RabbitListener(queues = Constants.QUEUE_ORDER_REJECTED)
    public void onOrderRejected(KitchenDecisionEvent event) {
        orderService.markRejected(event);
    }
}
