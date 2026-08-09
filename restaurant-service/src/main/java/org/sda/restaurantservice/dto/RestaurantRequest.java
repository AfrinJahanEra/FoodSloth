package org.sda.restaurantservice.dto;

import org.sda.restaurantservice.entity.Restaurant;

/**
 * What an admin sends to patch the restaurant profile. Only the editable profile fields are
 * accepted - {@code open}, {@code menu} and the ratings have their own endpoints, and the id is
 * never client-supplied.
 */
public record RestaurantRequest(
        String name,
        String description,
        String photo,
        String address,
        Double latitude,
        Double longitude,
        String phone) {

    public Restaurant toEntity() {
        Restaurant restaurant = new Restaurant();
        restaurant.setName(name);
        restaurant.setDescription(description);
        restaurant.setPhoto(photo);
        restaurant.setAddress(address);
        restaurant.setLatitude(latitude);
        restaurant.setLongitude(longitude);
        restaurant.setPhone(phone);
        return restaurant;
    }
}
