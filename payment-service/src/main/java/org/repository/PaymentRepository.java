package org.repository;

import org.entity.Payment;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends MongoRepository<Payment, String> {
    Optional<Payment> findByOrderId(String orderId);

    Optional<Payment> findByStripeSessionId(String stripeSessionId);

    List<Payment> findByUserId(String userId);

    /** The admin ledger, newest first. */
    List<Payment> findAllByOrderByCreatedAtDesc();
}
