package org.sda.restaurantservice.controller;

import org.sda.restaurantservice.entity.MenuItem;
import org.sda.restaurantservice.entity.OperatingHours;
import org.sda.restaurantservice.entity.Restaurant;
import org.sda.restaurantservice.service.RestaurantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Single-tenant: this whole service represents one restaurant, so there is no
 * restaurant id in these routes - only /restaurant (the one profile) and its menu.
 *
 * Auth note: api-gateway verifies the JWT and forwards the caller's role via the
 * X-User-Role header. This service does not parse tokens itself - it trusts the header,
 * which only api-gateway is allowed to set (client-supplied copies are stripped there).
 */
@RestController
@RequestMapping("/restaurant")
public class RestaurantController {

    @Autowired
    private RestaurantService restaurantService;

    @GetMapping
    public Restaurant getRestaurant() {
        return restaurantService.getRestaurant();
    }

    @PutMapping
    public Restaurant updateRestaurant(@RequestHeader(value = "X-User-Role", required = false) String role,
                                        @RequestBody Restaurant restaurant) {
        return restaurantService.updateRestaurant(role, restaurant);
    }

    @PatchMapping("/status")
    public Restaurant setOpenStatus(@RequestHeader(value = "X-User-Role", required = false) String role,
                                     @RequestBody Map<String, Boolean> body) {
        return restaurantService.setOpenStatus(role, Boolean.TRUE.equals(body.get("open")));
    }

    @PutMapping("/hours")
    public Restaurant updateOperatingHours(@RequestHeader(value = "X-User-Role", required = false) String role,
                                            @RequestBody List<OperatingHours> hours) {
        return restaurantService.updateOperatingHours(role, hours);
    }

    @GetMapping("/menu")
    public List<MenuItem> getMenu() {
        return restaurantService.getMenu();
    }

    @PostMapping("/menu")
    public Restaurant addMenuItem(@RequestHeader(value = "X-User-Role", required = false) String role,
                                   @RequestBody MenuItem item) {
        return restaurantService.addMenuItem(role, item);
    }

    @PutMapping("/menu/{itemId}")
    public Restaurant updateMenuItem(@RequestHeader(value = "X-User-Role", required = false) String role,
                                      @PathVariable String itemId,
                                      @RequestBody MenuItem item) {
        return restaurantService.updateMenuItem(role, itemId, item);
    }

    @DeleteMapping("/menu/{itemId}")
    public Restaurant deleteMenuItem(@RequestHeader(value = "X-User-Role", required = false) String role,
                                      @PathVariable String itemId) {
        return restaurantService.deleteMenuItem(role, itemId);
    }
}
