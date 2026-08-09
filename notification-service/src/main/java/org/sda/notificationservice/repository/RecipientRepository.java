package org.sda.notificationservice.repository;

import org.sda.notificationservice.entity.Recipient;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface RecipientRepository extends MongoRepository<Recipient, String> {

    /** Audience of the promotional broadcast: only customers who opted in. */
    List<Recipient> findByMarketingOptInIsTrue();
}
