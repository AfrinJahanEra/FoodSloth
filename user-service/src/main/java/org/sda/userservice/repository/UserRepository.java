package org.sda.userservice.repository;

import org.sda.userservice.entity.Role;
import org.sda.userservice.entity.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByEmail(String email);

    Optional<User> findByPhone(String phone);

    List<User> findByRole(Role role);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);
}
