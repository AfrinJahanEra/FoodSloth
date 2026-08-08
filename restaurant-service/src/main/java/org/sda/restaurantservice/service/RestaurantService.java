package org.sda.restaurantservice.service;

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
 *
 * Auth note: the JWT itself is verified by api-gateway, not here. The gateway forwards the
 * caller's identity via the X-User-Role (and X-User-Id) headers, which this service trusts.
 */
@Service
public class RestaurantService {

    @Autowired
    private RestaurantRepository restaurantRepository;

    public Restaurant getRestaurant() {
        List<Restaurant> all = restaurantRepository.findAll();
        if (!all.isEmpty()) {
            return all.get(0);
        }
        Restaurant restaurant = new Restaurant();
        restaurant.setName("My Restaurant");
        return restaurantRepository.save(restaurant);
    }

    public Restaurant updateRestaurant(String role, Restaurant updated) {
        requireAdmin(role);
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

    public Restaurant setOpenStatus(String role, boolean open) {
        requireAdmin(role);
        Restaurant restaurant = getRestaurant();
        restaurant.setOpen(open);
        return restaurantRepository.save(restaurant);
    }

    public Restaurant updateOperatingHours(String role, List<OperatingHours> hours) {
        requireAdmin(role);
        Restaurant restaurant = getRestaurant();
        restaurant.setOperatingHours(hours);
        return restaurantRepository.save(restaurant);
    }

    public List<MenuItem> getMenu() {
        return getRestaurant().getMenu();
    }

    public MenuItem getMenuItem(String itemId) {
        return getRestaurant().getMenu().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menu item not found"));
    }

    public Restaurant addMenuItem(String role, MenuItem item) {
        requireAdmin(role);
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

    public Restaurant updateMenuItem(String role, String itemId, MenuItem updated) {
        requireAdmin(role);
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

    public Restaurant deleteMenuItem(String role, String itemId) {
        requireAdmin(role);
        Restaurant restaurant = getRestaurant();
        boolean removed = restaurant.getMenu().removeIf(item -> item.getId().equals(itemId));
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Menu item not found");
        }
        return restaurantRepository.save(restaurant);
    }

    private void requireAdmin(String role) {
        if (role == null || role.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization token");
        }
        if (!"ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Restaurant admin role required");
        }
    }
}
