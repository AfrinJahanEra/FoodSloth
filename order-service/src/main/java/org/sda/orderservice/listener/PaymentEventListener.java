package org.sda.orderservice.listener;

import org.sda.orderservice.dto.event.PaymentFailedEvent;
import org.sda.orderservice.dto.event.PaymentSucceededEvent;
import org.sda.orderservice.messaging.Constants;
import org.sda.orderservice.service.OrderService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Payment results. {@code payment.succeeded} moves the order to CONFIRMED;
 * {@code payment.failed} moves it to PAYMENT_FAILED and the customer can retry.
 */
@Component
public class PaymentEventListener {

    private final OrderService orderService;

    public PaymentEventListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @RabbitListener(queues = Constants.QUEUE_PAYMENT_SUCCEEDED)
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        orderService.markConfirmed(event.orderId());
    }

    @RabbitListener(queues = Constants.QUEUE_PAYMENT_FAILED)
    public void onPaymentFailed(PaymentFailedEvent event) {
        orderService.markPaymentFailed(event.orderId());
    }
}
