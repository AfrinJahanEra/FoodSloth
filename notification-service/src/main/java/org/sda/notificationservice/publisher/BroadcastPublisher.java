package org.sda.notificationservice.publisher;

import org.sda.notificationservice.dto.event.MarketingBroadcastEvent;
import org.sda.notificationservice.entity.Channel;
import org.sda.notificationservice.messaging.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Publishes {@code marketing.broadcast}.
 *
 * <p>The only thing this service publishes, and it publishes to its own queue on purpose. Marketing
 * hits {@code POST /notifications/broadcast}, which returns as soon as the campaign is on the
 * broker; the fan-out then happens in the listener. So the request does not block on thousands of
 * sends, the campaign survives a restart, and a broadcast triggered by another service runs through
 * exactly the same code.
 */
@Component
public class BroadcastPublisher {

    private static final Logger log = LoggerFactory.getLogger(BroadcastPublisher.class);

    private final AmqpTemplate amqpTemplate;

    public BroadcastPublisher(AmqpTemplate amqpTemplate) {
        this.amqpTemplate = amqpTemplate;
    }

    /** @return the generated campaign id, which is also the deduplication key. */
    public String publish(String title, String body, List<Channel> channels) {
        String campaignId = UUID.randomUUID().toString();
        MarketingBroadcastEvent event = new MarketingBroadcastEvent(campaignId, title, body, channels);
        amqpTemplate.convertAndSend(Constants.EXCHANGE, Constants.RK_MARKETING_BROADCAST, event);
        log.info("Published {} as campaign {}", Constants.RK_MARKETING_BROADCAST, campaignId);
        return campaignId;
    }
}
