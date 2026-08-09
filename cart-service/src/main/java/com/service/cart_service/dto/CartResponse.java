package com.service.cart_service.dto;

import com.service.cart_service.entity.Cart;
import com.service.cart_service.entity.CartItem;

import java.util.List;

/**
 * The cart as the client sees it: who owns it and what they picked. Timestamps are internal
 * bookkeeping and stay off the wire.
 */
public record CartResponse(String id, String userId, List<ItemView> items) {

    public static CartResponse from(Cart cart) {
        return new CartResponse(cart.getId(), cart.getUserId(),
                cart.getItems().stream().map(ItemView::from).toList());
    }

    /** One cart line - deliberately just an item id and a quantity, prices belong to Restaurant Service. */
    public record ItemView(String itemId, int quantity) {

        public static ItemView from(CartItem item) {
            return new ItemView(item.getItemId(), item.getQuantity());
        }
    }
}
