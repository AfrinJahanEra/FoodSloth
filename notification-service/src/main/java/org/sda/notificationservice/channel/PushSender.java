package org.sda.notificationservice.channel;

import org.sda.notificationservice.entity.Channel;
import org.sda.notificationservice.entity.Recipient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Push sender used in development: writes the message to the log instead of calling Firebase.
 *
 * <p>Everything around it is real - the device tokens, the opt-out check, the stored audit record -
 * so pointing this at FCM means replacing the body of {@link #send} and nothing else.
 */
@Component
public class PushSender implements ChannelSender {

    private static final Logger log = LoggerFactory.getLogger(PushSender.class);

    @Override
    public Channel channel() {
        return Channel.PUSH;
    }

    @Override
    public boolean canReach(Recipient recipient) {
        return recipient.isPushEnabled() && !recipient.getDeviceTokens().isEmpty();
    }

    @Override
    public void send(Recipient recipient, String title, String body) {
        // One push per device the customer is signed in on.
        for (String token : recipient.getDeviceTokens()) {
            log.info("[PUSH -> {}] {} | {}", mask(token), title, body);
        }
    }

    /** Device tokens are credentials, so only the tail goes in the log. */
    private String mask(String token) {
        return token.length() <= 6 ? "***" : "***" + token.substring(token.length() - 6);
    }
}
