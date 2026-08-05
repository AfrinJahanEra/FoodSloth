package org.sda.orderservice.service;

import org.sda.orderservice.dto.CreateOrderRequest;
import org.sda.orderservice.dto.OrderItemRequest;
import org.sda.orderservice.dto.OrderResponse;
import org.sda.orderservice.entity.DeliveryType;
import org.sda.orderservice.entity.Order;
import org.sda.orderservice.entity.OrderItem;
import org.sda.orderservice.entity.OrderStatus;
import org.sda.orderservice.publisher.OrderEventPublisher;
import org.sda.orderservice.repository.OrderRepository;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private static final Set<OrderStatus> CANCELLABLE_STATUSES = EnumSet.of(OrderStatus.PENDING_PAYMENT, OrderStatus.CONFIRMED);
    private static final String ADMIN_ROLE = "ADMIN";

    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;
    private final JwtService jwtService;

    public OrderService(OrderRepository orderRepository, OrderEventPublisher eventPublisher, JwtService jwtService) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.jwtService = jwtService;
    }

    public OrderResponse createOrder(String authHeader, CreateOrderRequest request) {
        AuthenticatedUser user = authenticate(authHeader);
        Order order = buildOrder(user.userId(), request);
        Order saved = orderRepository.save(order);
        eventPublisher.publishPaymentRequested(saved);
        return toResponse(saved);
    }

    public OrderResponse getOrder(String authHeader, String id) {
        AuthenticatedUser user = authenticate(authHeader);
        Order order = findOrderOrThrow(id);
        requireOwnerOrAdmin(user, order);
        return toResponse(order);
    }

    public List<OrderResponse> getOrdersForUser(String authHeader, String userId) {
        AuthenticatedUser user = authenticate(authHeader);
        if (!user.userId().equals(userId) && !user.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot view another user's orders");
        }
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    public OrderResponse cancelOrder(String authHeader, String id) {
        AuthenticatedUser user = authenticate(authHeader);
        Order order = findOrderOrThrow(id);
        requireOwnerOrAdmin(user, order);
        if (!CANCELLABLE_STATUSES.contains(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Order cannot be cancelled once the restaurant has accepted it or after delivery");
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(Instant.now());
        Order saved = orderRepository.save(order);
        eventPublisher.publishOrderCancelled(saved);
        return toResponse(saved);
    }

    public OrderResponse reorder(String authHeader, String id) {
        AuthenticatedUser user = authenticate(authHeader);
        Order original = findOrderOrThrow(id);
        requireOwnerOrAdmin(user, original);
        List<OrderItemRequest> items = original.getItems().stream()
                .map(item -> new OrderItemRequest(item.getMenuItemId(), item.getName(), item.getPrice(), item.getQuantity()))
                .toList();
        // TODO: ideally re-validate item availability and refresh prices via Restaurant Service
        // before reordering, since the previous order's items are a point-in-time snapshot.
        CreateOrderRequest request = new CreateOrderRequest(
                original.getRestaurantId(), original.getDeliveryAddressId(),
                original.getDeliveryType(), original.getPaymentMethod(), items);
        return createOrder(authHeader, request);
    }

    public void markConfirmed(String orderId) {
        Order order = findOrderOrThrow(orderId);
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            log.warn("Ignoring PaymentSucceeded for order {} in unexpected status {}", orderId, order.getStatus());
            return;
        }
        order.setStatus(OrderStatus.CONFIRMED);
        order.setUpdatedAt(Instant.now());
        Order saved = orderRepository.save(order);
        eventPublisher.publishOrderConfirmed(saved);
    }

    public void markPaymentFailed(String orderId) {
        Order order = findOrderOrThrow(orderId);
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            log.warn("Ignoring PaymentFailed for order {} in unexpected status {}", orderId, order.getStatus());
            return;
        }
        order.setStatus(OrderStatus.PAYMENT_FAILED);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);
    }

    public void markPreparing(String orderId) {
        Order order = findOrderOrThrow(orderId);
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            log.warn("Ignoring OrderAccepted for order {} in unexpected status {}", orderId, order.getStatus());
            return;
        }
        order.setStatus(OrderStatus.PREPARING);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);
    }

    public void markOutForDelivery(String orderId) {
        Order order = findOrderOrThrow(orderId);
        if (order.getStatus() != OrderStatus.PREPARING) {
            log.warn("Ignoring DeliveryStarted for order {} in unexpected status {}", orderId, order.getStatus());
            return;
        }
        order.setStatus(OrderStatus.OUT_FOR_DELIVERY);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);
    }

    public void markDelivered(String orderId) {
        Order order = findOrderOrThrow(orderId);
        if (order.getStatus() != OrderStatus.OUT_FOR_DELIVERY) {
            log.warn("Ignoring DeliveryCompleted for order {} in unexpected status {}", orderId, order.getStatus());
            return;
        }
        order.setStatus(OrderStatus.DELIVERED);
        order.setUpdatedAt(Instant.now());
        Order saved = orderRepository.save(order);
        eventPublisher.publishOrderDelivered(saved);
    }

    private Order buildOrder(String userId, CreateOrderRequest request) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order must contain at least one item");
        }
        if (request.restaurantId() == null || request.restaurantId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "restaurantId is required");
        }
        if (request.deliveryType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "deliveryType is required");
        }

        List<OrderItem> items = request.items().stream().map(this::toOrderItem).toList();
        BigDecimal subtotal = items.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal deliveryCharge = calculateDeliveryCharge(request.deliveryType());
        // TODO: tax calculation is not implemented yet; no business rule has been provided.
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal grandTotal = subtotal.add(deliveryCharge).add(tax);

        Order order = new Order();
        order.setUserId(userId);
        order.setRestaurantId(request.restaurantId());
        order.setDeliveryAddressId(request.deliveryAddressId());
        order.setDeliveryType(request.deliveryType());
        order.setPaymentMethod(request.paymentMethod());
        order.setItems(items);
        order.setSubtotal(subtotal);
        order.setDeliveryCharge(deliveryCharge);
        order.setTax(tax);
        order.setGrandTotal(grandTotal);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());
        return order;
    }

    private OrderItem toOrderItem(OrderItemRequest request) {
        if (request.price() == null || request.quantity() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Each item requires a valid price and quantity");
        }
        OrderItem item = new OrderItem();
        item.setId(UUID.randomUUID().toString());
        item.setMenuItemId(request.menuItemId());
        item.setName(request.name());
        item.setPrice(request.price());
        item.setQuantity(request.quantity());
        item.setSubtotal(request.price().multiply(BigDecimal.valueOf(request.quantity())));
        return item;
    }

    // TODO: Delivery Service will eventually provide the real delivery charge based on
    // distance/time. These are placeholder flat fees until that integration exists.
    private BigDecimal calculateDeliveryCharge(DeliveryType deliveryType) {
        return switch (deliveryType) {
            case REGULAR -> BigDecimal.valueOf(60);
            case INSTANT -> BigDecimal.valueOf(120);
        };
    }

    private Order findOrderOrThrow(String id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + id));
    }

    private void requireOwnerOrAdmin(AuthenticatedUser user, Order order) {
        if (!user.userId().equals(order.getUserId()) && !user.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to access this order");
        }
    }

    private AuthenticatedUser authenticate(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
        }
        String token = authHeader.substring(7);
        try {
            return new AuthenticatedUser(jwtService.extractUserId(token), jwtService.extractRole(token));
        } catch (JwtException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }
    }

    private record AuthenticatedUser(String userId, String role) {
        boolean isAdmin() {
            return ADMIN_ROLE.equals(role);
        }
    }

    private OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(), order.getUserId(), order.getRestaurantId(), order.getDeliveryAddressId(),
                order.getDeliveryType(), order.getPaymentMethod(), order.getItems(),
                order.getSubtotal(), order.getDeliveryCharge(), order.getTax(), order.getGrandTotal(),
                order.getStatus(), order.getCreatedAt(), order.getUpdatedAt());
    }
}
