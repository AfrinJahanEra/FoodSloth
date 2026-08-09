package org.sda.restaurantservice.controller;

import org.sda.restaurantservice.dto.KitchenOrderResponse;
import org.sda.restaurantservice.entity.KitchenOrderStatus;
import org.sda.restaurantservice.service.KitchenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * The kitchen screen. Staff-only, reached through api-gateway at /restaurant/kitchen/**.
 *
 * Auth note: api-gateway verifies the JWT and forwards the caller's role via X-User-Role, which
 * this service trusts - client-supplied copies of that header are stripped at the gateway.
 *
 * Nothing here is called by another service. Order Service learns the kitchen's decisions from
 * restaurant.order-accepted / restaurant.order-rejected / restaurant.order-ready.
 */
@RestController
@RequestMapping("/restaurant/kitchen")
public class KitchenController {

    @Autowired
    private KitchenService kitchenService;

    /** Paid orders waiting to be taken on, plus what is already cooking. Oldest first. */
    @GetMapping("/orders")
    public List<KitchenOrderResponse> getActiveQueue(@RequestHeader(value = "X-User-Role", required = false) String role) {
        return kitchenService.getActiveQueue(role).stream().map(KitchenOrderResponse::from).toList();
    }

    @GetMapping("/orders/status/{status}")
    public List<KitchenOrderResponse> getByStatus(@RequestHeader(value = "X-User-Role", required = false) String role,
                                          @PathVariable KitchenOrderStatus status) {
        return kitchenService.getByStatus(role, status).stream().map(KitchenOrderResponse::from).toList();
    }

    @GetMapping("/orders/{orderId}")
    public KitchenOrderResponse getTicket(@RequestHeader(value = "X-User-Role", required = false) String role,
                                  @PathVariable String orderId) {
        return KitchenOrderResponse.from(kitchenService.getTicket(role, orderId));
    }

    /** The kitchen takes the order on; the customer's order moves to PREPARING. */
    @PatchMapping("/orders/{orderId}/accept")
    public KitchenOrderResponse accept(@RequestHeader(value = "X-User-Role", required = false) String role,
                               @PathVariable String orderId) {
        return KitchenOrderResponse.from(kitchenService.accept(role, orderId));
    }

    @PatchMapping("/orders/{orderId}/reject")
    public KitchenOrderResponse reject(@RequestHeader(value = "X-User-Role", required = false) String role,
                               @PathVariable String orderId,
                               @RequestBody(required = false) Map<String, String> body) {
        return KitchenOrderResponse.from(kitchenService.reject(role, orderId, body == null ? null : body.get("reason")));
    }

    /** The food is packed; this is what sets Delivery Service looking for a rider. */
    @PatchMapping("/orders/{orderId}/ready")
    public KitchenOrderResponse markReady(@RequestHeader(value = "X-User-Role", required = false) String role,
                                  @PathVariable String orderId) {
        return KitchenOrderResponse.from(kitchenService.markReady(role, orderId));
    }
}
