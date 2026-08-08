package org.sda.orderservice.publisher;

import org.sda.orderservice.dto.event.OrderCancelledEvent;
import org.sda.orderservice.dto.event.OrderConfirmedEvent;
import org.sda.orderservice.dto.event.OrderDeliveredEvent;
import org.sda.orderservice.dto.event.PaymentRequestedEvent;
import org.sda.orderservice.entity.Order;
import org.sda.orderservice.messaging.Constants;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public OrderEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishPaymentRequested(Order order) {
        PaymentRequestedEvent event = new PaymentRequestedEvent(
                order.getId(), order.getUserId(), order.getGrandTotal(), order.getPaymentMethod());
        rabbitTemplate.convertAndSend(Constants.ORDER_EXCHANGE, Constants.ROUTING_KEY_PAYMENT_REQUESTED, event);
    }

    public void publishOrderConfirmed(Order order) {
        OrderConfirmedEvent event = new OrderConfirmedEvent(order.getId(), order.getRestaurantId(), order.getUserId());
        rabbitTemplate.convertAndSend(Constants.ORDER_EXCHANGE, Constants.ROUTING_KEY_ORDER_CONFIRMED, event);
    }

    public void publishOrderCancelled(Order order) {
        OrderCancelledEvent event = new OrderCancelledEvent(order.getId(), order.getUserId(), order.getRestaurantId());
        rabbitTemplate.convertAndSend(Constants.ORDER_EXCHANGE, Constants.ROUTING_KEY_ORDER_CANCELLED, event);
    }

    public void publishOrderDelivered(Order order) {
        OrderDeliveredEvent event = new OrderDeliveredEvent(order.getId(), order.getUserId(), order.getRestaurantId());
        rabbitTemplate.convertAndSend(Constants.ORDER_EXCHANGE, Constants.ROUTING_KEY_ORDER_DELIVERED, event);
    }
}
