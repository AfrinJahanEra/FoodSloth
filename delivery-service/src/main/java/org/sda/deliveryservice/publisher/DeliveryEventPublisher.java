package org.sda.deliveryservice.publisher;

import org.sda.deliveryservice.dto.event.DeliveryAssignedEvent;
import org.sda.deliveryservice.dto.event.DeliveryCompletedEvent;
import org.sda.deliveryservice.dto.event.DeliveryStartedEvent;
import org.sda.deliveryservice.entity.Delivery;
import org.sda.deliveryservice.messaging.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.stereotype.Component;

/**
 * The only outbound channel this service has. Delivery Service makes no REST calls to any other
 * service - progress is announced here and whoever cares binds a queue to it.
 */
@Component
public class DeliveryEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DeliveryEventPublisher.class);

    private final AmqpTemplate amqpTemplate;

    public DeliveryEventPublisher(AmqpTemplate amqpTemplate) {
        this.amqpTemplate = amqpTemplate;
    }

    public void publishAssigned(Delivery delivery) {
        DeliveryAssignedEvent event = new DeliveryAssignedEvent(
                delivery.getOrderId(),
                delivery.getOrderNo(),
                delivery.getUserId(),
                delivery.getId(),
                delivery.getRiderId(),
                delivery.getRiderDisplayName(),
                delivery.getRiderPhone(),
                delivery.getEtaMinutes());
        send(Constants.RK_DELIVERY_ASSIGNED, event, delivery.getOrderId());
    }

    public void publishStarted(Delivery delivery) {
        DeliveryStartedEvent event = new DeliveryStartedEvent(
                delivery.getOrderId(),
                delivery.getOrderNo(),
                delivery.getUserId(),
                delivery.getId(),
                delivery.getRiderId(),
                delivery.getEtaMinutes());
        send(Constants.RK_DELIVERY_STARTED, event, delivery.getOrderId());
    }

    public void publishCompleted(Delivery delivery) {
        DeliveryCompletedEvent event = new DeliveryCompletedEvent(
                delivery.getOrderId(),
                delivery.getOrderNo(),
                delivery.getUserId(),
                delivery.getId(),
                delivery.getRiderId(),
                delivery.getDeliveredAt());
        send(Constants.RK_DELIVERY_COMPLETED, event, delivery.getOrderId());
    }

    private void send(String routingKey, Object event, String orderId) {
        amqpTemplate.convertAndSend(Constants.EXCHANGE, routingKey, event);
        log.info("Published {} for order {}", routingKey, orderId);
    }
}
