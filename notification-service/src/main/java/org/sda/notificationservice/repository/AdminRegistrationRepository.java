package org.sda.notificationservice.repository;

import org.sda.notificationservice.entity.AdminRegistration;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AdminRegistrationRepository extends MongoRepository<AdminRegistration, String> {
}
