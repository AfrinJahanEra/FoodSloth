package org.sda.notificationservice.channel;

import org.sda.notificationservice.entity.Channel;
import org.sda.notificationservice.entity.Recipient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Transactional SMS sender used in development: writes the message to the log instead of calling
 * Twilio. Replace the body of {@link #send} to go live; nothing else in the service changes.
 *
 * <p>SMS is reserved for the few things worth paying per message for - payment failures and the
 * rider being at the door - so the wording is deliberately short.
 */
@Component
public class SmsSender implements ChannelSender {

    private static final Logger log = LoggerFactory.getLogger(SmsSender.class);

    @Override
    public Channel channel() {
        return Channel.SMS;
    }

    @Override
    public boolean canReach(Recipient recipient) {
        return recipient.isSmsEnabled() && recipient.getPhone() != null && !recipient.getPhone().isBlank();
    }

    @Override
    public void send(Recipient recipient, String title, String body) {
        log.info("[SMS -> {}] {}", recipient.getPhone(), body);
    }
}
