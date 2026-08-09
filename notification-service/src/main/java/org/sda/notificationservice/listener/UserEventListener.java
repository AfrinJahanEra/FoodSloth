package org.sda.notificationservice.listener;

import org.sda.notificationservice.dto.event.UserRegisteredEvent;
import org.sda.notificationservice.messaging.Constants;
import org.sda.notificationservice.service.NotificationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** User Service events - how this service learns who the platform admins are. */
@Component
public class UserEventListener {

    private final NotificationService notificationService;

    public UserEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = Constants.QUEUE_USER_REGISTERED)
    public void onUserRegistered(UserRegisteredEvent event) {
        notificationService.onUserRegistered(event);
    }
}
