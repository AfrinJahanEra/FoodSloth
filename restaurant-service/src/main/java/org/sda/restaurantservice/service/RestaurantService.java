package org.sda.restaurantservice.service;

import io.jsonwebtoken.JwtException;
import org.sda.restaurantservice.entity.MenuItem;
import org.sda.restaurantservice.entity.OperatingHours;
import org.sda.restaurantservice.entity.Restaurant;
import org.sda.restaurantservice.repository.RestaurantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Single-tenant service: this whole app is built for one restaurant, so there is exactly
 * one Restaurant document. It is lazily created on first read/write instead of requiring
 * a separate "create restaurant" call.
 */
@Service
public class RestaurantService {

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private JwtService jwtService;

    public Restaurant getRestaurant() {
        List<Restaurant> all = restaurantRepository.findAll();
        if (!all.isEmpty()) {
            return all.get(0);
        }
        Restaurant restaurant = new Restaurant();
        restaurant.setName("My Restaurant");
        return restaurantRepository.save(restaurant);
    }

    public Restaurant updateRestaurant(String authHeader, Restaurant updated) {
        requireAdmin(authHeader);
        Restaurant restaurant = getRestaurant();
        if (updated.getName() != null && !updated.getName().isBlank()) {
            restaurant.setName(updated.getName());
        }
        if (updated.getDescription() != null) {
            restaurant.setDescription(updated.getDescription());
        }
        if (updated.getPhoto() != null) {
            restaurant.setPhoto(updated.getPhoto());
        }
        if (updated.getAddress() != null) {
            restaurant.setAddress(updated.getAddress());
        }
        if (updated.getPhone() != null) {
            restaurant.setPhone(updated.getPhone());
        }
        if (updated.getLatitude() != null) {
            restaurant.setLatitude(updated.getLatitude());
        }
        if (updated.getLongitude() != null) {
            restaurant.setLongitude(updated.getLongitude());
        }
        return restaurantRepository.save(restaurant);
    }

    public Restaurant setOpenStatus(String authHeader, boolean open) {
        requireAdmin(authHeader);
        Restaurant restaurant = getRestaurant();
        restaurant.setOpen(open);
        return restaurantRepository.save(restaurant);
    }

    public Restaurant updateOperatingHours(String authHeader, List<OperatingHours> hours) {
        requireAdmin(authHeader);
        Restaurant restaurant = getRestaurant();
        restaurant.setOperatingHours(hours);
        return restaurantRepository.save(restaurant);
    }

    public List<MenuItem> getMenu() {
        return getRestaurant().getMenu();
    }

    public Restaurant addMenuItem(String authHeader, MenuItem item) {
        requireAdmin(authHeader);
        if (item.getName() == null || item.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Menu item name is required");
        }
        if (item.getPrice() == null || item.getPrice() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A non-negative menu item price is required");
        }
        Restaurant restaurant = getRestaurant();
        item.setId(UUID.randomUUID().toString());
        restaurant.getMenu().add(item);
        return restaurantRepository.save(restaurant);
    }

    public Restaurant updateMenuItem(String authHeader, String itemId, MenuItem updated) {
        requireAdmin(authHeader);
        Restaurant restaurant = getRestaurant();
        MenuItem existing = restaurant.getMenu().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menu item not found"));

        if (updated.getName() != null && !updated.getName().isBlank()) {
            existing.setName(updated.getName());
        }
        if (updated.getDescription() != null) {
            existing.setDescription(updated.getDescription());
        }
        if (updated.getCategory() != null) {
            existing.setCategory(updated.getCategory());
        }
        if (updated.getPhoto() != null) {
            existing.setPhoto(updated.getPhoto());
        }
        if (updated.getPrice() != null && updated.getPrice() >= 0) {
            existing.setPrice(updated.getPrice());
        }
        if (updated.getAvailable() != null) {
            existing.setAvailable(updated.getAvailable());
        }
        return restaurantRepository.save(restaurant);
    }

    public Restaurant deleteMenuItem(String authHeader, String itemId) {
        requireAdmin(authHeader);
        Restaurant restaurant = getRestaurant();
        boolean removed = restaurant.getMenu().removeIf(item -> item.getId().equals(itemId));
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Menu item not found");
        }
        return restaurantRepository.save(restaurant);
    }

    private void requireAdmin(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
        }
        String token = authHeader.substring(7);
        String role;
        try {
            role = jwtService.extractRole(token);
        } catch (JwtException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }
        if (!"ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Restaurant admin role required");
        }
    }
}
