package org.sda.deliveryservice.repository;

import org.sda.deliveryservice.entity.Delivery;
import org.sda.deliveryservice.entity.DeliveryStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface DeliveryRepository extends MongoRepository<Delivery, String> {

    Optional<Delivery> findByOrderId(String orderId);

    List<Delivery> findByStatus(DeliveryStatus status);

    List<Delivery> findByUserIdOrderByCreatedAtDesc(String userId);

    /**
     * A rider may hold several live jobs at once (the admin assigns while a slot is free), so
     * this returns a list; callers take the oldest one as the current job.
     */
    List<Delivery> findByRiderIdAndStatusInOrderByAssignedAtAsc(String riderId, List<DeliveryStatus> statuses);

    List<Delivery> findByRiderIdOrderByCreatedAtDesc(String riderId);
}
