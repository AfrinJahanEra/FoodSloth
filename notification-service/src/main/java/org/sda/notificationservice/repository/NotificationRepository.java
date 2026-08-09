package org.sda.notificationservice.repository;

import org.sda.notificationservice.entity.Notification;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface NotificationRepository extends MongoRepository<Notification, String> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(String userId);

    List<Notification> findByOrderIdOrderByCreatedAtDesc(String orderId);

    long countByUserIdAndReadIsFalse(String userId);

    boolean existsByEventKey(String eventKey);
}
