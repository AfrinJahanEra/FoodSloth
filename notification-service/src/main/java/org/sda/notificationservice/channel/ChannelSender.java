package org.sda.notificationservice.channel;

import org.sda.notificationservice.entity.Channel;
import org.sda.notificationservice.entity.Recipient;

/**
 * One way of actually delivering a message.
 *
 * <p>The rest of the service only knows this interface, so swapping the development sender for
 * Firebase, SendGrid or Twilio is a one-class change with nothing else touched.
 *
 * <p>Implementations throw when the provider rejects the message; the caller records that as a
 * FAILED notification. Returning normally means "handed to the provider".
 */
public interface ChannelSender {

    Channel channel();

    /**
     * Says whether this recipient can be reached on this channel at all - a registered device
     * token, an email address, a phone number - and has not switched the channel off.
     */
    boolean canReach(Recipient recipient);

    void send(Recipient recipient, String title, String body);
}
