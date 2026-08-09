package org.sda.restaurantservice.repository;

import org.sda.restaurantservice.entity.KitchenOrder;
import org.sda.restaurantservice.entity.KitchenOrderStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface KitchenOrderRepository extends MongoRepository<KitchenOrder, String> {

    /** The kitchen screen: everything currently in one stage, oldest ticket first. */
    List<KitchenOrder> findByStatusOrderByCreatedAtAsc(KitchenOrderStatus status);

    List<KitchenOrder> findByStatusInOrderByCreatedAtAsc(List<KitchenOrderStatus> statuses);
}
