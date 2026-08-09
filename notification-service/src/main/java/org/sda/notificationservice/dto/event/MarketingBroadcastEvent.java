package org.sda.notificationservice.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.sda.notificationservice.entity.Channel;

import java.util.List;

/**
 * Consumed from {@code marketing.broadcast} - a campaign to fan out to every customer who opted in.
 *
 * <p>Published by this service's own {@code POST /notifications/broadcast} endpoint, and equally by
 * any other service that wants to reach customers (a restaurant announcing a discount, for
 * instance). Both paths land on the same queue, so there is one implementation of the fan-out.
 *
 * <p>{@code campaignId} is the correlation key and the deduplication key: replaying the same
 * campaign id will not message anybody twice.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MarketingBroadcastEvent(
        String campaignId,
        String title,
        String body,
        /** Channels to use. Empty or null means push only, which is the cheapest option. */
        List<Channel> channels
) {
}
