package org.publisher;

import org.dto.event.PaymentFailedEvent;
import org.dto.event.PaymentSucceededEvent;
import org.entity.Payment;
import org.messaging.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.stereotype.Component;

/**
 * The only outbound channel this service has. Payment Service used to PUT the result to
 * order-service over HTTP, which meant a payment could clear while the order silently never heard
 * about it. Now the result is published once and Order Service picks it up whenever it is running.
 */
@Component
public class PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);

    private final AmqpTemplate amqpTemplate;

    public PaymentEventPublisher(AmqpTemplate amqpTemplate) {
        this.amqpTemplate = amqpTemplate;
    }

    public void publishSucceeded(Payment payment) {
        PaymentSucceededEvent event = new PaymentSucceededEvent(
                payment.getOrderId(),
                payment.getOrderNo(),
                payment.getUserId(),
                payment.getId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getPaymentMethod());
        send(Constants.RK_PAYMENT_SUCCEEDED, event, payment.getOrderId());
    }

    public void publishFailed(Payment payment, String reason) {
        PaymentFailedEvent event = new PaymentFailedEvent(
                payment.getOrderId(),
                payment.getOrderNo(),
                payment.getUserId(),
                payment.getId(),
                reason);
        send(Constants.RK_PAYMENT_FAILED, event, payment.getOrderId());
    }

    private void send(String routingKey, Object event, String orderId) {
        amqpTemplate.convertAndSend(Constants.EXCHANGE, routingKey, event);
        log.info("Published {} for order {}", routingKey, orderId);
    }
}
