package org.sda.deliveryservice.repository;

import org.sda.deliveryservice.entity.Rider;
import org.sda.deliveryservice.entity.RiderStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface RiderRepository extends MongoRepository<Rider, String> {

    List<Rider> findByStatus(RiderStatus status);
}
