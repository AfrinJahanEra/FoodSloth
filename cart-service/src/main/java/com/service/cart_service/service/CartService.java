package com.service.cart_service.service;

import com.service.cart_service.Constants;
import com.service.cart_service.entity.Cart;
import com.service.cart_service.entity.CartItem;
import com.service.cart_service.event.CartCheckedOutEvent;
import com.service.cart_service.repository.CartRepository;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Qualifier;

import java.time.Instant;

/**
 * Cart is treated as a transient shopping session: one active cart per user.
 * There's a single restaurant for the whole app, so there's no notion of
 * "items from different restaurants" to reconcile. Coupons are only "held"
 * here (stored on the cart) - actual validation/discount application happens
 * in Order Service at checkout, per the project's service boundaries.
 * <p>
 * Item name/price/availability are never trusted from the client - they're
 * fetched live from Restaurant Service (via RestaurantClient) on every add
 * and quantity update. Cart Service keeps no local copy of the menu.
 * <p>
 * NOTE: userId is currently taken directly from the URL path rather than a
 * JWT, since only user-service issues/validates tokens so far. Swap
 * getCart/etc. to derive userId from an Authorization header once a shared
 * auth mechanism exists across services.
 */
@Service
public class CartService {

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private RestaurantClient restaurantClient;

    @Autowired
    @Qualifier("template")
    private AmqpTemplate amqpTemplate;

    @Value("${cart.tax-rate}")
    private double taxRate;

    @Value("${cart.delivery-fee}")
    private double deliveryFee;

    public Cart getCart(String userId) {
        return cartRepository.findByUserId(userId).orElseGet(() -> emptyCart(userId));
    }

    public Cart addItem(String userId, String itemId, int quantity) {
        if (itemId == null || itemId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "itemId is required");
        }
        if (quantity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity must be positive");
        }

        MenuItemResponse menuItem = restaurantClient.getMenuItem(itemId);
        if (!menuItem.available()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Item is currently unavailable: " + itemId);
        }

        Cart cart = cartRepository.findByUserId(userId).orElseGet(() -> emptyCart(userId));

        CartItem existing = cart.getItems().stream()
                .filter(i -> i.getItemId().equals(itemId))
                .findFirst()
                .orElse(null);

        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + quantity);
            existing.setName(menuItem.name());
            existing.setPrice(menuItem.price());
            existing.setPhoto(menuItem.photo());
        } else {
            CartItem item = new CartItem();
            item.setItemId(itemId);
            item.setName(menuItem.name());
            item.setPrice(menuItem.price());
            item.setPhoto(menuItem.photo());
            item.setQuantity(quantity);
            cart.getItems().add(item);
        }

        return recalculateAndSave(cart);
    }

    public Cart updateItemQuantity(String userId, String itemId, int quantity) {
        Cart cart = requireCart(userId);
        CartItem item = cart.getItems().stream()
                .filter(i -> i.getItemId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found in cart"));

        if (quantity <= 0) {
            cart.getItems().remove(item);
        } else {
            MenuItemResponse menuItem = restaurantClient.getMenuItem(itemId);
            if (!menuItem.available()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Item is currently unavailable: " + itemId);
            }
            item.setQuantity(quantity);
            item.setName(menuItem.name());
            item.setPrice(menuItem.price());
            item.setPhoto(menuItem.photo());
        }

        clearCouponIfEmpty(cart);
        return recalculateAndSave(cart);
    }

    public Cart removeItem(String userId, String itemId) {
        Cart cart = requireCart(userId);
        boolean removed = cart.getItems().removeIf(i -> i.getItemId().equals(itemId));
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found in cart");
        }

        clearCouponIfEmpty(cart);
        return recalculateAndSave(cart);
    }

    public Cart applyCoupon(String userId, String couponCode) {
        if (couponCode == null || couponCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "couponCode is required");
        }
        Cart cart = requireCart(userId);
        if (cart.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot apply a coupon to an empty cart");
        }
        cart.setCouponCode(couponCode);
        return recalculateAndSave(cart);
    }

    public Cart removeCoupon(String userId) {
        Cart cart = requireCart(userId);
        cart.setCouponCode(null);
        return recalculateAndSave(cart);
    }

    public void clearCart(String userId) {
        Cart cart = cartRepository.findByUserId(userId).orElse(null);
        if (cart == null) {
            return;
        }
        cart.getItems().clear();
        cart.setCouponCode(null);
        recalculateAndSave(cart);
    }

    /**
     * Finalizes the cart: publishes a CartCheckedOut event (via the existing
     * exchange/routing key) for Order Service to pick up, then empties the
     * cart. Cart Service's job ends here - it doesn't wait for or track what
     * Order Service does with the event.
     */
    public CartCheckedOutEvent checkout(String userId) {
        Cart cart = requireCart(userId);
        if (cart.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot checkout an empty cart");
        }

        CartCheckedOutEvent event = new CartCheckedOutEvent(
                "CartCheckedOut",
                cart.getId(),
                cart.getUserId(),
                cart.getItems(),
                cart.getCouponCode(),
                cart.getSubtotal(),
                cart.getTax(),
                cart.getDeliveryFee(),
                cart.getTotal(),
                Instant.now()
        );

        amqpTemplate.convertAndSend(Constants.EXCHANGE, Constants.ROUTING_KEY, event);

        clearCart(userId);

        return event;
    }

    private void clearCouponIfEmpty(Cart cart) {
        if (cart.getItems().isEmpty()) {
            cart.setCouponCode(null);
        }
    }

    private Cart requireCart(String userId) {
        return cartRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart not found"));
    }

    private Cart emptyCart(String userId) {
        Cart cart = new Cart();
        cart.setUserId(userId);
        return cart;
    }

    private Cart recalculateAndSave(Cart cart) {
        double subtotal = 0.0;
        for (CartItem item : cart.getItems()) {
            double lineTotal = round(item.getPrice() * item.getQuantity());
            item.setLineTotal(lineTotal);
            subtotal += lineTotal;
        }
        subtotal = round(subtotal);

        boolean empty = cart.getItems().isEmpty();
        double tax = empty ? 0.0 : round(subtotal * taxRate);
        double delivery = empty ? 0.0 : round(deliveryFee);
        double total = round(subtotal + tax + delivery);

        cart.setSubtotal(subtotal);
        cart.setTax(tax);
        cart.setDeliveryFee(delivery);
        cart.setTotal(total);
        cart.setUpdatedAt(Instant.now());

        return cartRepository.save(cart);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
