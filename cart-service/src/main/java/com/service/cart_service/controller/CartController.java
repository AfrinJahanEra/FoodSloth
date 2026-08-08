package com.service.cart_service.controller;

import com.service.cart_service.entity.Cart;
import com.service.cart_service.event.CartCheckedOutEvent;
import com.service.cart_service.service.CartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/carts")
public class CartController {

    @Autowired
    private CartService cartService;

    @GetMapping("/{userId}")
    public Cart getCart(@PathVariable String userId) {
        return cartService.getCart(userId);
    }

    @PostMapping("/{userId}/items")
    public Cart addItem(@PathVariable String userId, @RequestBody AddItemRequest request) {
        return cartService.addItem(userId, request.itemId(), request.quantity());
    }

    @PutMapping("/{userId}/items/{itemId}")
    public Cart updateItem(@PathVariable String userId, @PathVariable String itemId,
                            @RequestBody UpdateItemRequest request) {
        return cartService.updateItemQuantity(userId, itemId, request.quantity());
    }

    @DeleteMapping("/{userId}/items/{itemId}")
    public Cart removeItem(@PathVariable String userId, @PathVariable String itemId) {
        return cartService.removeItem(userId, itemId);
    }

    @PutMapping("/{userId}/coupon")
    public Cart applyCoupon(@PathVariable String userId, @RequestBody CouponRequest request) {
        return cartService.applyCoupon(userId, request.couponCode());
    }

    @DeleteMapping("/{userId}/coupon")
    public Cart removeCoupon(@PathVariable String userId) {
        return cartService.removeCoupon(userId);
    }

    @DeleteMapping("/{userId}")
    public void clearCart(@PathVariable String userId) {
        cartService.clearCart(userId);
    }

    @PostMapping("/{userId}/checkout")
    public CartCheckedOutEvent checkout(@PathVariable String userId) {
        return cartService.checkout(userId);
    }

    public record AddItemRequest(String itemId, int quantity) {
    }

    public record UpdateItemRequest(int quantity) {
    }

    public record CouponRequest(String couponCode) {
    }
}
