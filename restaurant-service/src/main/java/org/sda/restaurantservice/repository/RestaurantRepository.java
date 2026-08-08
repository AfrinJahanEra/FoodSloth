package org.sda.restaurantservice.repository;

import org.sda.restaurantservice.entity.Restaurant;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RestaurantRepository extends MongoRepository<Restaurant, String> {
}
