package org.listener;

import org.dto.event.OrderCancelledEvent;
import org.dto.event.PaymentRequestedEvent;
import org.messaging.Constants;
import org.service.PaymentService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * The only way work enters this service. No other service calls Payment Service over HTTP.
 *
 * <p>If this service is down, payment requests wait in {@code payment.order-requested.queue} and are
 * handled the moment it comes back, so checkout never fails because payment was restarting.
 */
@Component
public class OrderEventListener {

    private final PaymentService paymentService;

    public OrderEventListener(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @RabbitListener(queues = Constants.QUEUE_PAYMENT_REQUESTED)
    public void onPaymentRequested(PaymentRequestedEvent event) {
        paymentService.onPaymentRequested(event);
    }

    @RabbitListener(queues = Constants.QUEUE_ORDER_CANCELLED)
    public void onOrderCancelled(OrderCancelledEvent event) {
        paymentService.onOrderCancelled(event.orderId());
    }
}
