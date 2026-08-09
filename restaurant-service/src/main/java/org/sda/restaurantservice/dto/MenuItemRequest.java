package org.sda.restaurantservice.dto;

import org.sda.restaurantservice.entity.MenuItem;

/**
 * What an admin sends to create or patch a menu item. The id is minted by this service and is
 * never accepted from the client.
 */
public record MenuItemRequest(
        String name,
        String description,
        String category,
        Double price,
        String photo,
        Boolean available) {

    public MenuItem toEntity() {
        MenuItem item = new MenuItem();
        item.setName(name);
        item.setDescription(description);
        item.setCategory(category);
        item.setPrice(price);
        item.setPhoto(photo);
        item.setAvailable(available);
        return item;
    }
}
