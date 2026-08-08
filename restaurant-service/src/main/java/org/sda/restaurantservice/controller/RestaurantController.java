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
    public Restaurant updateRestaurant(@RequestHeader("Authorization") String authHeader,
                                        @RequestBody Restaurant restaurant) {
        return restaurantService.updateRestaurant(authHeader, restaurant);
    }

    @PatchMapping("/status")
    public Restaurant setOpenStatus(@RequestHeader("Authorization") String authHeader,
                                     @RequestBody Map<String, Boolean> body) {
        return restaurantService.setOpenStatus(authHeader, Boolean.TRUE.equals(body.get("open")));
    }

    @PutMapping("/hours")
    public Restaurant updateOperatingHours(@RequestHeader("Authorization") String authHeader,
                                            @RequestBody List<OperatingHours> hours) {
        return restaurantService.updateOperatingHours(authHeader, hours);
    }

    @GetMapping("/menu")
    public List<MenuItem> getMenu() {
        return restaurantService.getMenu();
    }

    @PostMapping("/menu")
    public Restaurant addMenuItem(@RequestHeader("Authorization") String authHeader,
                                   @RequestBody MenuItem item) {
        return restaurantService.addMenuItem(authHeader, item);
    }

    @PutMapping("/menu/{itemId}")
    public Restaurant updateMenuItem(@RequestHeader("Authorization") String authHeader,
                                      @PathVariable String itemId,
                                      @RequestBody MenuItem item) {
        return restaurantService.updateMenuItem(authHeader, itemId, item);
    }

    @DeleteMapping("/menu/{itemId}")
    public Restaurant deleteMenuItem(@RequestHeader("Authorization") String authHeader,
                                      @PathVariable String itemId) {
        return restaurantService.deleteMenuItem(authHeader, itemId);
    }
}
