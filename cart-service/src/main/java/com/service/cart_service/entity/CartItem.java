package com.service.cart_service.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One line in the cart. Deliberately just a menu item id and a quantity - no name, photo or price.
 *
 * <p>Prices belong to Restaurant Service. Keeping a copy here would mean the cart could hold a
 * stale price, and checkout would need a live lookup that couples the two services again. Instead
 * the cart ships the ids at checkout and Restaurant Service prices them fresh.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartItem {
    private String itemId;
    private int quantity;
}
