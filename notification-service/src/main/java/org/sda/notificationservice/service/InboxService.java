package org.sda.notificationservice.service;

import org.sda.notificationservice.entity.Notification;
import org.sda.notificationservice.repository.NotificationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * The customer's in-app notification list, read back over REST.
 *
 * <p>Separate from {@link NotificationService} because this is a read/update path driven by the
 * client, while that one is a write path driven by RabbitMQ.
 */
@Service
public class InboxService {

    private final NotificationRepository notificationRepository;

    public InboxService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public List<Notification> listForUser(String userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public long unreadCount(String userId) {
        return notificationRepository.countByUserIdAndReadIsFalse(userId);
    }

    public Notification markRead(String userId, String notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (!userId.equals(notification.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This notification belongs to another user");
        }
        notification.setRead(true);
        return notificationRepository.save(notification);
    }

    public long markAllRead(String userId) {
        List<Notification> unread = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(notification -> !notification.isRead())
                .peek(notification -> notification.setRead(true))
                .toList();
        notificationRepository.saveAll(unread);
        return unread.size();
    }

    /** Support view: every message sent about one order, whichever channel it went out on. */
    public List<Notification> listForOrder(String orderId) {
        return notificationRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
    }
}
