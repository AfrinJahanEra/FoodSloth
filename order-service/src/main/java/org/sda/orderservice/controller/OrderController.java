package org.sda.orderservice.controller;

import org.sda.orderservice.dto.OrderResponse;
import org.sda.orderservice.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * REST surface of Order Service. Reached by clients through api-gateway at {@code /orders/**}.
 *
 * <p>There is no endpoint to create an order: orders are created by {@code restaurant.order-priced}.
 * The client's flow is POST /carts/{userId}/checkout (which returns the orderId immediately) and
 * then polling GET /orders/{orderId} here. A 404 from that poll simply means pricing has not
 * finished yet - the client keeps polling.
 *
 * <p>Auth note: the JWT is verified by api-gateway, which strips client-supplied {@code X-User-*}
 * headers and re-adds them from the verified claims. This service trusts those headers and does
 * not parse tokens itself.
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/{id}")
    public OrderResponse getOrder(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                  @RequestHeader(value = "X-User-Role", required = false) String role,
                                  @PathVariable String id) {
        return orderService.getOrder(requireAuthenticated(userId), role, id);
    }

    @GetMapping("/me")
    public List<OrderResponse> getMyOrders(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                           @RequestHeader(value = "X-User-Role", required = false) String role) {
        String caller = requireAuthenticated(userId);
        return orderService.getOrdersForUser(caller, role, caller);
    }

    @PatchMapping("/{id}/cancel")
    public OrderResponse cancelOrder(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                     @RequestHeader(value = "X-User-Role", required = false) String role,
                                     @PathVariable String id) {
        return orderService.cancelOrder(requireAuthenticated(userId), role, id);
    }

    /**
     * Orders this order again at today's prices. Returns 202 with the fresh orderId as soon as the
     * reorder request is on the broker; the client then polls GET /orders/{orderId} as usual.
     */
    @PostMapping("/{id}/reorder")
    public ResponseEntity<Map<String, String>> reorder(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                                       @RequestHeader(value = "X-User-Role", required = false) String role,
                                                       @PathVariable String id) {
        String newOrderId = orderService.reorder(requireAuthenticated(userId), role, id);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("orderId", newOrderId));
    }

    // ------------------------------------------------------------------
    // Gateway-header guard
    // ------------------------------------------------------------------

    private String requireAuthenticated(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization token");
        }
        return userId;
    }
}
