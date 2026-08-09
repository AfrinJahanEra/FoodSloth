package org.sda.notificationservice.service;

import org.sda.notificationservice.channel.ChannelSender;
import org.sda.notificationservice.entity.Channel;
import org.sda.notificationservice.entity.Notification;
import org.sda.notificationservice.entity.NotificationStatus;
import org.sda.notificationservice.entity.NotificationType;
import org.sda.notificationservice.entity.Recipient;
import org.sda.notificationservice.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Turns "this happened to this customer" into messages on the requested channels, and records what
 * came of each attempt.
 *
 * <p>Wording is decided by {@link NotificationService}; everything mechanical lives here: finding
 * the recipient, honouring opt-outs, calling the provider, storing the audit row and refusing to
 * send the same thing twice.
 *
 * <p>Nothing in this class throws on a delivery problem. These calls are made from RabbitMQ
 * listeners, and throwing would only bounce a message that a retry cannot fix - a missing email
 * address will still be missing next time. Failures are recorded instead.
 */
@Service
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final Map<Channel, ChannelSender> senders = new EnumMap<>(Channel.class);
    private final RecipientService recipientService;
    private final NotificationRepository notificationRepository;

    public NotificationDispatcher(List<ChannelSender> channelSenders,
                                  RecipientService recipientService,
                                  NotificationRepository notificationRepository) {
        channelSenders.forEach(sender -> this.senders.put(sender.channel(), sender));
        this.recipientService = recipientService;
        this.notificationRepository = notificationRepository;
    }

    /**
     * Sends one notification to one customer on each of {@code channels}.
     *
     * @param dedupeScope stable identifier for this event, normally {@code <routingKey>:<orderId>}.
     *                    Combined with the channel it becomes the unique key that makes a
     *                    redelivered message harmless.
     */
    public void dispatch(String dedupeScope, String userId, String orderId, NotificationType type,
                         String title, String body, List<Channel> channels) {
        Recipient recipient = recipientService.find(userId).orElse(null);
        if (recipient == null) {
            log.info("User {} has no notification profile; recording {} as skipped", userId, type);
        }
        channels.forEach(channel ->
                deliver(recipient, dedupeScope, userId, orderId, type, title, body, channel));
    }

    /**
     * Same as {@link #dispatch} but for an already-loaded recipient, so a broadcast to thousands of
     * customers does not re-read each one.
     */
    public void dispatchTo(Recipient recipient, String dedupeScope, NotificationType type,
                           String title, String body, List<Channel> channels) {
        channels.forEach(channel ->
                deliver(recipient, dedupeScope, recipient.getId(), null, type, title, body, channel));
    }

    private void deliver(Recipient recipient, String dedupeScope, String userId, String orderId,
                         NotificationType type, String title, String body, Channel channel) {
        String eventKey = dedupeScope + ":" + channel;
        if (notificationRepository.existsByEventKey(eventKey)) {
            log.debug("Already handled {}; not sending again", eventKey);
            return;
        }

        Notification record = new Notification();
        record.setUserId(userId);
        record.setOrderId(orderId);
        record.setType(type);
        record.setChannel(channel);
        record.setTitle(title);
        record.setBody(body);
        record.setEventKey(eventKey);

        ChannelSender sender = senders.get(channel);
        if (recipient == null) {
            skip(record, "No notification profile registered for this user");
        } else if (sender == null) {
            skip(record, "No sender configured for channel " + channel);
        } else if (!sender.canReach(recipient)) {
            skip(record, "Channel " + channel + " is switched off or has no address");
        } else {
            try {
                sender.send(recipient, title, body);
                record.setStatus(NotificationStatus.SENT);
            } catch (RuntimeException e) {
                record.setStatus(NotificationStatus.FAILED);
                record.setFailureReason(e.getMessage());
                log.warn("{} notification failed for user {}: {}", channel, userId, e.getMessage());
            }
        }

        try {
            notificationRepository.save(record);
        } catch (DuplicateKeyException e) {
            // Two redeliveries raced. The other one won and the customer has been told once.
            log.debug("Concurrent duplicate for {}; discarding", eventKey);
        }
    }

    private void skip(Notification record, String reason) {
        record.setStatus(NotificationStatus.SKIPPED);
        record.setFailureReason(reason);
    }
}
