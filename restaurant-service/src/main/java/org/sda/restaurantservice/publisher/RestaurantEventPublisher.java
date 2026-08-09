package org.sda.restaurantservice.publisher;

import org.sda.restaurantservice.dto.event.KitchenDecisionEvent;
import org.sda.restaurantservice.dto.event.OrderPricedEvent;
import org.sda.restaurantservice.dto.event.OrderReadyEvent;
import org.sda.restaurantservice.dto.event.OrderUnavailableEvent;
import org.sda.restaurantservice.messaging.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.stereotype.Component;

/**
 * The only outbound channel this service has. Restaurant Service makes no REST calls to any other
 * service - it announces what happened and whoever cares binds a queue to it.
 */
@Component
public class RestaurantEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RestaurantEventPublisher.class);

    private final AmqpTemplate amqpTemplate;

    public RestaurantEventPublisher(AmqpTemplate amqpTemplate) {
        this.amqpTemplate = amqpTemplate;
    }

    public void publishPriced(OrderPricedEvent event) {
        send(Constants.RK_ORDER_PRICED, event, event.orderId());
    }

    public void publishUnavailable(OrderUnavailableEvent event) {
        send(Constants.RK_ORDER_UNAVAILABLE, event, event.orderId());
    }

    public void publishAccepted(KitchenDecisionEvent event) {
        send(Constants.RK_ORDER_ACCEPTED, event, event.orderId());
    }

    public void publishRejected(KitchenDecisionEvent event) {
        send(Constants.RK_ORDER_REJECTED, event, event.orderId());
    }

    public void publishReady(OrderReadyEvent event) {
        send(Constants.RK_ORDER_READY, event, event.orderId());
    }

    private void send(String routingKey, Object event, String orderId) {
        amqpTemplate.convertAndSend(Constants.EXCHANGE, routingKey, event);
        log.info("Published {} for order {}", routingKey, orderId);
    }
}
