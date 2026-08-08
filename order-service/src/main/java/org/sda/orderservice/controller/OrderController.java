package org.sda.orderservice.controller;

import org.sda.orderservice.dto.CreateOrderRequest;
import org.sda.orderservice.dto.OrderResponse;
import org.sda.orderservice.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@RequestHeader("Authorization") String authHeader,
                                                       @RequestBody CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(authHeader, request));
    }

    @GetMapping("/{id}")
    public OrderResponse getOrder(@RequestHeader("Authorization") String authHeader, @PathVariable String id) {
        return orderService.getOrder(authHeader, id);
    }

    @GetMapping("/user/{userId}")
    public List<OrderResponse> getOrdersForUser(@RequestHeader("Authorization") String authHeader, @PathVariable String userId) {
        return orderService.getOrdersForUser(authHeader, userId);
    }

    @PatchMapping("/{id}/cancel")
    public OrderResponse cancelOrder(@RequestHeader("Authorization") String authHeader, @PathVariable String id) {
        return orderService.cancelOrder(authHeader, id);
    }

    @PostMapping("/{id}/reorder")
    public ResponseEntity<OrderResponse> reorder(@RequestHeader("Authorization") String authHeader, @PathVariable String id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.reorder(authHeader, id));
    }
}
