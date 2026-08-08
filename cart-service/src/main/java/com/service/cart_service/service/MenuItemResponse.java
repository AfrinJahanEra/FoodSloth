package com.service.cart_service.service;

/**
 * Contract expected from Restaurant Service's menu item lookup:
 * GET /restaurant/menu/{itemId}
 * -> { "id": "...", "name": "...", "price": 350.00, "photo": "...", "available": true }
 * <p>
 * Cart Service never stores its own copy of the menu (no replication) -
 * this is fetched fresh on every add/update so name, price, photo, and
 * availability always reflect what Restaurant Service currently has.
 */
public record MenuItemResponse(String id, String name, double price, String photo, boolean available) {
}
