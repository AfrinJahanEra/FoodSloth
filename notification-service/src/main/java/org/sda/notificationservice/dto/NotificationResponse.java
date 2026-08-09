package org.sda.notificationservice.dto;

import org.sda.notificationservice.entity.Channel;
import org.sda.notificationservice.entity.Notification;
import org.sda.notificationservice.entity.NotificationStatus;
import org.sda.notificationservice.entity.NotificationType;

import java.time.Instant;

/**
 * One inbox entry as the notification list renders it. {@code eventKey} is the internal
 * deduplication handle and never leaves this service.
 */
public record NotificationResponse(
        String id,
        String userId,
        String orderId,
        NotificationType type,
        Channel channel,
        String title,
        String body,
        NotificationStatus status,
        String failureReason,
        boolean read,
        Instant createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getUserId(), notification.getOrderId(),
                notification.getType(), notification.getChannel(), notification.getTitle(), notification.getBody(),
                notification.getStatus(), notification.getFailureReason(), notification.isRead(),
                notification.getCreatedAt());
    }
}
