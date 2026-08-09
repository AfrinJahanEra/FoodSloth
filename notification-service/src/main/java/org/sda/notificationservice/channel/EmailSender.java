package org.sda.notificationservice.channel;

import org.sda.notificationservice.entity.Channel;
import org.sda.notificationservice.entity.Recipient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Transactional email sender used in development: writes the message to the log instead of calling
 * an SMTP relay. Replace the body of {@link #send} with a JavaMailSender or SendGrid call to go
 * live; nothing else in the service changes.
 */
@Component
public class EmailSender implements ChannelSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    @Override
    public Channel channel() {
        return Channel.EMAIL;
    }

    @Override
    public boolean canReach(Recipient recipient) {
        return recipient.isEmailEnabled() && recipient.getEmail() != null && !recipient.getEmail().isBlank();
    }

    @Override
    public void send(Recipient recipient, String subject, String body) {
        log.info("[EMAIL -> {}] {}\n{}", recipient.getEmail(), subject, body);
    }
}
