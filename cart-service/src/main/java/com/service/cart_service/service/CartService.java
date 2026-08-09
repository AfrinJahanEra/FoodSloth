package com.service.cart_service.service;

import com.service.cart_service.dto.CheckoutRequest;
import com.service.cart_service.dto.event.CartCheckedOutEvent;
import com.service.cart_service.entity.Cart;
import com.service.cart_service.entity.CartItem;
import com.service.cart_service.messaging.Constants;
import com.service.cart_service.repository.CartRepository;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

/**
 * Cart is a transient shopping session: one active cart per user, holding nothing but what the
 * customer picked (item id + quantity).
 *
 * <p>There is deliberately no price lookup and no menu copy here. Names, prices and availability
 * belong to Restaurant Service; asking for them would couple the two services again. The cart
 * ships the item ids at checkout and Restaurant Service prices them fresh, so a stale price can
 * never reach a customer's order.
 *
 * <p>Checkout mints the {@code orderId} (a UUID) and publishes {@code cart.checked-out}. From that
 * moment the cart's job is done: pricing, payment and fulfilment are all downstream events that
 * this service neither waits for nor tracks. The client gets the id back immediately (HTTP 202)
 * and polls {@code GET /orders/{orderId}} on Order Service.
 */
@Service
public class CartService {

    private final CartRepository cartRepository;
    private final AmqpTemplate amqpTemplate;

    public CartService(CartRepository cartRepository, AmqpTemplate amqpTemplate) {
        this.cartRepository = cartRepository;
        this.amqpTemplate = amqpTemplate;
    }

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

        Cart cart = cartRepository.findByUserId(userId).orElseGet(() -> emptyCart(userId));
        CartItem existing = cart.getItems().stream()
                .filter(i -> i.getItemId().equals(itemId))
                .findFirst()
                .orElse(null);

        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + quantity);
        } else {
            cart.getItems().add(new CartItem(itemId, quantity));
        }
        return save(cart);
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
            item.setQuantity(quantity);
        }
        return save(cart);
    }

    public Cart removeItem(String userId, String itemId) {
        Cart cart = requireCart(userId);
        boolean removed = cart.getItems().removeIf(i -> i.getItemId().equals(itemId));
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found in cart");
        }
        return save(cart);
    }

    public void clearCart(String userId) {
        Cart cart = cartRepository.findByUserId(userId).orElse(null);
        if (cart == null) {
            return;
        }
        cart.getItems().clear();
        save(cart);
    }

    /**
     * Publishes {@code cart.checked-out} with a freshly minted orderId and empties the cart.
     *
     * <p>Publishing happens before clearing: if the broker were unreachable the exception stops the
     * flow with the cart intact, so the customer can simply retry instead of losing their basket.
     */
    public String checkout(String userId, CheckoutRequest request) {
        if (request.deliveryAddress() == null || request.deliveryAddress().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "deliveryAddress is required");
        }

        Cart cart = requireCart(userId);
        if (cart.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot checkout an empty cart");
        }

        String orderId = UUID.randomUUID().toString();

        CartCheckedOutEvent event = new CartCheckedOutEvent(
                orderId,
                userId,
                cart.getItems().stream()
                        .map(i -> new CartCheckedOutEvent.CheckoutItem(i.getItemId(), i.getQuantity()))
                        .toList(),
                request.deliveryAddress(),
                request.deliveryLatitude(),
                request.deliveryLongitude(),
                request.paymentMethod(),
                request.note());

        amqpTemplate.convertAndSend(Constants.EXCHANGE, Constants.RK_CART_CHECKED_OUT, event);

        cart.getItems().clear();
        save(cart);

        return orderId;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Cart requireCart(String userId) {
        return cartRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart not found"));
    }

    private Cart emptyCart(String userId) {
        Cart cart = new Cart();
        cart.setUserId(userId);
        return cart;
    }

    private Cart save(Cart cart) {
        cart.setUpdatedAt(Instant.now());
        return cartRepository.save(cart);
    }
}
