package org.sda.restaurantservice.dto;

import org.sda.restaurantservice.entity.MenuItem;

/**
 * A menu item as clients see it. Same field names the SPA has always read; the entity stays a
 * persistence detail behind this contract.
 */
public record MenuItemResponse(
        String id,
        String name,
        String description,
        String category,
        Double price,
        String photo,
        Boolean available) {

    public static MenuItemResponse from(MenuItem item) {
        return new MenuItemResponse(item.getId(), item.getName(), item.getDescription(), item.getCategory(),
                item.getPrice(), item.getPhoto(), item.getAvailable());
    }
}
