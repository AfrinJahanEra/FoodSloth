package org.sda.notificationservice.listener;

import org.sda.notificationservice.dto.event.MarketingBroadcastEvent;
import org.sda.notificationservice.messaging.Constants;
import org.sda.notificationservice.service.NotificationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * The promotional broadcast channel.
 *
 * <p>Receives campaigns from marketing's own REST endpoint and from any other service that wants to
 * reach customers, and fans them out to everyone who opted in.
 */
@Component
public class MarketingEventListener {

    private final NotificationService notificationService;

    public MarketingEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = Constants.QUEUE_MARKETING_BROADCAST)
    public void onBroadcast(MarketingBroadcastEvent event) {
        notificationService.onMarketingBroadcast(event);
    }
}
