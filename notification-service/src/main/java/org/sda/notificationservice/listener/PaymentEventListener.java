package org.sda.notificationservice.listener;

import org.sda.notificationservice.dto.event.PaymentFailedEvent;
import org.sda.notificationservice.dto.event.PaymentSucceededEvent;
import org.sda.notificationservice.messaging.Constants;
import org.sda.notificationservice.service.NotificationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Payment Service events - the source of the transactional payment receipt. */
@Component
public class PaymentEventListener {

    private final NotificationService notificationService;

    public PaymentEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = Constants.QUEUE_PAYMENT_SUCCEEDED)
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        notificationService.onPaymentSucceeded(event);
    }

    @RabbitListener(queues = Constants.QUEUE_PAYMENT_FAILED)
    public void onPaymentFailed(PaymentFailedEvent event) {
        notificationService.onPaymentFailed(event);
    }
}
