package org.sda.notificationservice.repository;

import org.sda.notificationservice.entity.Recipient;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RecipientRepository extends MongoRepository<Recipient, String> {
}
