package org.sda.orderservice.publisher;

import org.sda.orderservice.dto.event.OrderCancelledEvent;
import org.sda.orderservice.dto.event.OrderConfirmedEvent;
import org.sda.orderservice.dto.event.OrderDeliveredEvent;
import org.sda.orderservice.dto.event.PaymentRequestedEvent;
import org.sda.orderservice.entity.Order;
import org.sda.orderservice.messaging.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * The only outbound channel this service has. Order Service makes no REST calls to any other
 * service - every state change it owes somebody is announced here and whoever cares binds a queue
 * to it.
 */
@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public OrderEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishPaymentRequested(Order order) {
        PaymentRequestedEvent event = new PaymentRequestedEvent(
                order.getId(),
                order.getOrderNo(),
                order.getUserId(),
                order.getGrandTotal() == null ? null : order.getGrandTotal().doubleValue(),
                order.getCurrency(),
                order.getPaymentMethod() == null ? null : order.getPaymentMethod().name(),
                order.getRestaurantName() == null ? "Order " + order.getId() : order.getRestaurantName());
        send(Constants.RK_PAYMENT_REQUESTED, event, order.getId());
    }

    public void publishOrderConfirmed(Order order) {
        OrderConfirmedEvent event = new OrderConfirmedEvent(
                order.getId(),
                order.getOrderNo(),
                order.getUserId(),
                order.getRestaurantId(),
                order.getGrandTotal() == null ? null : order.getGrandTotal().doubleValue());
        send(Constants.RK_ORDER_CONFIRMED, event, order.getId());
    }

    public void publishOrderCancelled(Order order, String reason) {
        OrderCancelledEvent event = new OrderCancelledEvent(
                order.getId(), order.getOrderNo(), order.getUserId(), order.getRestaurantId(), reason);
        send(Constants.RK_ORDER_CANCELLED, event, order.getId());
    }

    public void publishOrderDelivered(Order order) {
        OrderDeliveredEvent event = new OrderDeliveredEvent(
                order.getId(), order.getOrderNo(), order.getUserId(), order.getRestaurantId());
        send(Constants.RK_ORDER_DELIVERED, event, order.getId());
    }

    private void send(String routingKey, Object event, String orderId) {
        rabbitTemplate.convertAndSend(Constants.EXCHANGE, routingKey, event);
        log.info("Published {} for order {}", routingKey, orderId);
    }
}
