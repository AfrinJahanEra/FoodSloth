package com.service.cart_service.repository;

import com.service.cart_service.entity.Cart;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface CartRepository extends MongoRepository<Cart, String> {
    Optional<Cart> findByUserId(String userId);

    void deleteByUserId(String userId);
}
