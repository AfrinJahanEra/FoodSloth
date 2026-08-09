package org.sda.restaurantservice.dto;

import org.sda.restaurantservice.entity.OperatingHours;
import org.sda.restaurantservice.entity.Restaurant;

import java.util.List;

/**
 * The restaurant profile as clients see it - the banner on the menu page and the settings form
 * on the kitchen screen. {@code createdAt} is a persistence detail and stays internal.
 */
public record RestaurantResponse(
        String id,
        String name,
        String description,
        String photo,
        String address,
        Double latitude,
        Double longitude,
        String phone,
        List<HoursView> operatingHours,
        boolean open,
        double averageRating,
        List<MenuItemResponse> menu) {

    public static RestaurantResponse from(Restaurant restaurant) {
        return new RestaurantResponse(
                restaurant.getId(),
                restaurant.getName(),
                restaurant.getDescription(),
                restaurant.getPhoto(),
                restaurant.getAddress(),
                restaurant.getLatitude(),
                restaurant.getLongitude(),
                restaurant.getPhone(),
                restaurant.getOperatingHours().stream().map(HoursView::from).toList(),
                restaurant.isOpen(),
                restaurant.getAverageRating(),
                restaurant.getMenu().stream().map(MenuItemResponse::from).toList());
    }

    /** One row of the weekly opening schedule, exactly as the settings form edits it. */
    public record HoursView(String day, String openTime, String closeTime) {

        public static HoursView from(OperatingHours hours) {
            return new HoursView(hours.getDay(), hours.getOpenTime(), hours.getCloseTime());
        }
    }
}
