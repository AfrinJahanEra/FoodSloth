package org.sda.restaurantservice.controller;

import org.sda.restaurantservice.dto.HoursRequest;
import org.sda.restaurantservice.dto.MenuItemRequest;
import org.sda.restaurantservice.dto.MenuItemResponse;
import org.sda.restaurantservice.dto.RestaurantRequest;
import org.sda.restaurantservice.dto.RestaurantResponse;
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
    public RestaurantResponse getRestaurant() {
        return RestaurantResponse.from(restaurantService.getRestaurant());
    }

    @PutMapping
    public RestaurantResponse updateRestaurant(@RequestHeader(value = "X-User-Role", required = false) String role,
                                        @RequestBody RestaurantRequest restaurant) {
        return RestaurantResponse.from(restaurantService.updateRestaurant(role, restaurant.toEntity()));
    }

    @PatchMapping("/status")
    public RestaurantResponse setOpenStatus(@RequestHeader(value = "X-User-Role", required = false) String role,
                                     @RequestBody Map<String, Boolean> body) {
        return RestaurantResponse.from(restaurantService.setOpenStatus(role, Boolean.TRUE.equals(body.get("open"))));
    }

    @PutMapping("/hours")
    public RestaurantResponse updateOperatingHours(@RequestHeader(value = "X-User-Role", required = false) String role,
                                            @RequestBody List<HoursRequest> hours) {
        return RestaurantResponse.from(restaurantService.updateOperatingHours(role,
                hours.stream().map(HoursRequest::toEntity).toList()));
    }

    @GetMapping("/menu")
    public List<MenuItemResponse> getMenu() {
        return restaurantService.getMenu().stream().map(MenuItemResponse::from).toList();
    }

    @GetMapping("/menu/{itemId}")
    public MenuItemResponse getMenuItem(@PathVariable String itemId) {
        return MenuItemResponse.from(restaurantService.getMenuItem(itemId));
    }

    @PostMapping("/menu")
    public RestaurantResponse addMenuItem(@RequestHeader(value = "X-User-Role", required = false) String role,
                                   @RequestBody MenuItemRequest item) {
        return RestaurantResponse.from(restaurantService.addMenuItem(role, item.toEntity()));
    }

    @PutMapping("/menu/{itemId}")
    public RestaurantResponse updateMenuItem(@RequestHeader(value = "X-User-Role", required = false) String role,
                                      @PathVariable String itemId,
                                      @RequestBody MenuItemRequest item) {
        return RestaurantResponse.from(restaurantService.updateMenuItem(role, itemId, item.toEntity()));
    }

    @DeleteMapping("/menu/{itemId}")
    public RestaurantResponse deleteMenuItem(@RequestHeader(value = "X-User-Role", required = false) String role,
                                      @PathVariable String itemId) {
        return RestaurantResponse.from(restaurantService.deleteMenuItem(role, itemId));
    }
}
