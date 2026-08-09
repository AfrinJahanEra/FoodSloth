package com.service.cart_service.controller;

import com.service.cart_service.dto.CartResponse;
import com.service.cart_service.dto.CheckoutRequest;
import com.service.cart_service.entity.Cart;
import com.service.cart_service.service.CartService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * REST surface of Cart Service. Reached by clients through api-gateway at {@code /carts/**}.
 *
 * <p>Auth note: the JWT is verified by api-gateway, which strips client-supplied {@code X-User-*}
 * headers and re-adds them from the verified claims. The path carries the cart owner and must
 * match the authenticated user, so nobody can shop or check out with somebody else's id.
 *
 * <p>Checkout does not call any other service. It publishes {@code cart.checked-out} and returns
 * the freshly minted orderId as a 202; pricing and order creation happen downstream over the
 * broker, which is why this endpoint stays instant even if Restaurant or Order Service is down.
 */
@RestController
@RequestMapping("/carts")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping("/{userId}")
    public CartResponse getCart(@RequestHeader(value = "X-User-Id", required = false) String callerId,
                        @PathVariable String userId) {
        return CartResponse.from(cartService.getCart(requireOwner(callerId, userId)));
    }

    @PostMapping("/{userId}/items")
    public CartResponse addItem(@RequestHeader(value = "X-User-Id", required = false) String callerId,
                        @PathVariable String userId,
                        @RequestBody AddItemRequest request) {
        return CartResponse.from(cartService.addItem(requireOwner(callerId, userId), request.itemId(), request.quantity()));
    }

    @PutMapping("/{userId}/items/{itemId}")
    public CartResponse updateItem(@RequestHeader(value = "X-User-Id", required = false) String callerId,
                           @PathVariable String userId,
                           @PathVariable String itemId,
                           @RequestBody UpdateItemRequest request) {
        return CartResponse.from(cartService.updateItemQuantity(requireOwner(callerId, userId), itemId, request.quantity()));
    }

    @DeleteMapping("/{userId}/items/{itemId}")
    public CartResponse removeItem(@RequestHeader(value = "X-User-Id", required = false) String callerId,
                           @PathVariable String userId,
                           @PathVariable String itemId) {
        return CartResponse.from(cartService.removeItem(requireOwner(callerId, userId), itemId));
    }

    @DeleteMapping("/{userId}")
    public void clearCart(@RequestHeader(value = "X-User-Id", required = false) String callerId,
                          @PathVariable String userId) {
        cartService.clearCart(requireOwner(callerId, userId));
    }

    /**
     * Starts the order pipeline. Returns 202 with the orderId as soon as the event is on the
     * broker - the client then polls {@code GET /orders/{orderId}} for progress.
     */
    @PostMapping("/{userId}/checkout")
    public ResponseEntity<Map<String, String>> checkout(@RequestHeader(value = "X-User-Id", required = false) String callerId,
                                                        @PathVariable String userId,
                                                        @RequestBody CheckoutRequest request) {
        String orderId = cartService.checkout(requireOwner(callerId, userId), request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("orderId", orderId));
    }

    // ------------------------------------------------------------------
    // Gateway-header guard
    // ------------------------------------------------------------------

    private String requireOwner(String callerId, String userId) {
        if (callerId == null || callerId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization token");
        }
        if (!callerId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only use your own cart");
        }
        return userId;
    }

    public record AddItemRequest(String itemId, int quantity) {
    }

    public record UpdateItemRequest(int quantity) {
    }
}
