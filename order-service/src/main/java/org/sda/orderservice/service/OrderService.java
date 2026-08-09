package org.sda.orderservice.service;

import org.sda.orderservice.dto.OrderResponse;
import org.sda.orderservice.dto.event.KitchenDecisionEvent;
import org.sda.orderservice.dto.event.OrderPricedEvent;
import org.sda.orderservice.dto.event.OrderUnavailableEvent;
import org.sda.orderservice.dto.event.ReorderRequestedEvent;
import org.sda.orderservice.entity.Order;
import org.sda.orderservice.entity.OrderItem;
import org.sda.orderservice.entity.OrderStatus;
import org.sda.orderservice.entity.PaymentMethod;
import org.sda.orderservice.messaging.Constants;
import org.sda.orderservice.publisher.OrderEventPublisher;
import org.sda.orderservice.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The order lifecycle. This service never prices anything and never calls another service:
 *
 * <ul>
 *   <li>{@code restaurant.order-priced} creates the order (PENDING_PAYMENT) and asks for payment</li>
 *   <li>{@code payment.succeeded} confirms it, {@code payment.failed} fails it</li>
 *   <li>{@code restaurant.order-accepted/rejected} moves it to PREPARING or REJECTED</li>
 *   <li>{@code delivery.started/completed} moves it to OUT_FOR_DELIVERY and DELIVERED</li>
 *   <li>the customer can cancel while it is still early</li>
 * </ul>
 *
 * <p>The order id is the UUID Cart Service minted at checkout, so the client can poll
 * {@code GET /orders/{orderId}} from the very first moment.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private static final Set<OrderStatus> CANCELLABLE_STATUSES =
            EnumSet.of(OrderStatus.PENDING_PAYMENT, OrderStatus.CONFIRMED);
    private static final String ADMIN_ROLE = "ADMIN";

    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;
    private final RabbitTemplate rabbitTemplate;

    public OrderService(OrderRepository orderRepository, OrderEventPublisher eventPublisher,
                        RabbitTemplate rabbitTemplate) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.rabbitTemplate = rabbitTemplate;
    }

    // ------------------------------------------------------------------
    // Driven by events
    // ------------------------------------------------------------------

    /**
     * Builds the order from the pricing snapshot and asks Payment Service for the money.
     * Idempotent on the order id: a redelivered priced event is ignored once the order exists.
     */
    public void onOrderPriced(OrderPricedEvent priced) {
        if (priced.orderId() == null || priced.orderId().isBlank()) {
            log.warn("Ignoring a priced order with no orderId - it cannot be correlated");
            return;
        }
        if (orderRepository.existsById(priced.orderId())) {
            log.info("Order {} already exists; ignoring the repeat priced event", priced.orderId());
            return;
        }

        Order order = buildOrderFrom(priced);
        Order saved = orderRepository.save(order);
        eventPublisher.publishPaymentRequested(saved);
    }

    /**
     * The checkout could not be priced, so there is nothing to pay for. A closed order document is
     * still saved so the client's poll of {@code GET /orders/{orderId}} gets a definitive answer
     * with the reason, and {@code order.cancelled} is raised so Notification Service can tell the
     * customer what went wrong.
     */
    public void onOrderUnavailable(OrderUnavailableEvent unavailable) {
        if (unavailable.orderId() == null || orderRepository.existsById(unavailable.orderId())) {
            return;
        }

        Order order = new Order();
        order.setId(unavailable.orderId());
        order.setUserId(unavailable.userId());
        order.setRestaurantId(unavailable.restaurantId());
        order.setStatus(OrderStatus.REJECTED);
        order.setNote(unavailable.reason());
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());

        Order saved = orderRepository.save(order);
        eventPublisher.publishOrderCancelled(saved, unavailable.reason());
    }

    public void markConfirmed(String orderId) {
        transition(orderId, OrderStatus.PENDING_PAYMENT, OrderStatus.CONFIRMED, "payment.succeeded");
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order != null && order.getStatus() == OrderStatus.CONFIRMED) {
            eventPublisher.publishOrderConfirmed(order);
        }
    }

    public void markPaymentFailed(String orderId) {
        transition(orderId, OrderStatus.PENDING_PAYMENT, OrderStatus.PAYMENT_FAILED, "payment.failed");
    }

    public void markPreparing(String orderId) {
        transition(orderId, OrderStatus.CONFIRMED, OrderStatus.PREPARING, "restaurant.order-accepted");
    }

    /**
     * The kitchen turned down a paid order. The status is final, and {@code order.cancelled} is
     * raised so Payment Service refunds the card charge.
     */
    public void markRejected(KitchenDecisionEvent rejected) {
        Order order = orderRepository.findById(rejected.orderId()).orElse(null);
        if (order == null) {
            log.warn("restaurant.order-rejected for unknown order {}", rejected.orderId());
            return;
        }
        if (order.getStatus() != OrderStatus.CONFIRMED && order.getStatus() != OrderStatus.PREPARING) {
            log.warn("Ignoring restaurant.order-rejected for order {} in status {}",
                    rejected.orderId(), order.getStatus());
            return;
        }

        String reason = rejected.reason() == null || rejected.reason().isBlank()
                ? "The restaurant could not take this order"
                : rejected.reason();
        order.setStatus(OrderStatus.REJECTED);
        order.setNote(reason);
        order.setUpdatedAt(Instant.now());
        Order saved = orderRepository.save(order);
        eventPublisher.publishOrderCancelled(saved, reason);
    }

    public void markOutForDelivery(String orderId) {
        transition(orderId, OrderStatus.PREPARING, OrderStatus.OUT_FOR_DELIVERY, "delivery.started");
    }

    public void markDelivered(String orderId) {
        transition(orderId, OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED, "delivery.completed");
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order != null && order.getStatus() == OrderStatus.DELIVERED) {
            eventPublisher.publishOrderDelivered(order);
        }
    }

    // ------------------------------------------------------------------
    // Driven by customers over REST
    // ------------------------------------------------------------------

    public OrderResponse getOrder(String userId, String role, String id) {
        Order order = findOrderOrThrow(id);
        requireOwnerOrAdmin(userId, role, order);
        return toResponse(order);
    }

    public List<OrderResponse> getOrdersForUser(String userId, String role, String targetUserId) {
        if (!userId.equals(targetUserId) && !ADMIN_ROLE.equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot view another user's orders");
        }
        return orderRepository.findByUserIdOrderByCreatedAtDesc(targetUserId).stream()
                .map(this::toResponse)
                .toList();
    }

    public OrderResponse cancelOrder(String userId, String role, String id) {
        Order order = findOrderOrThrow(id);
        requireOwnerOrAdmin(userId, role, order);
        if (!CANCELLABLE_STATUSES.contains(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Order cannot be cancelled once the restaurant has accepted it or after delivery");
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(Instant.now());
        Order saved = orderRepository.save(order);
        eventPublisher.publishOrderCancelled(saved, "Cancelled by the customer");
        return toResponse(saved);
    }

    /**
     * Orders a past order again. A fresh orderId is minted and {@code order.reorder-requested} is
     * published; Restaurant Service re-prices it at today's prices, and the usual pipeline takes
     * over from there. Returns the new orderId immediately so the client can start polling.
     */
    public String reorder(String userId, String role, String id) {
        Order original = findOrderOrThrow(id);
        requireOwnerOrAdmin(userId, role, original);
        if (original.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This order has no items to reorder");
        }

        String newOrderId = UUID.randomUUID().toString();
        List<ReorderRequestedEvent.ReorderItem> items = original.getItems().stream()
                .map(item -> new ReorderRequestedEvent.ReorderItem(item.getMenuItemId(), item.getQuantity()))
                .toList();

        ReorderRequestedEvent event = new ReorderRequestedEvent(
                newOrderId,
                original.getUserId(),
                items,
                original.getDeliveryAddress(),
                original.getDeliveryLatitude(),
                original.getDeliveryLongitude(),
                original.getPaymentMethod() == null ? PaymentMethod.CARD.name() : original.getPaymentMethod().name(),
                original.getNote());

        rabbitTemplate.convertAndSend(Constants.EXCHANGE, Constants.RK_REORDER_REQUESTED, event);
        log.info("Published {} for new order {}", Constants.RK_REORDER_REQUESTED, newOrderId);
        return newOrderId;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Order buildOrderFrom(OrderPricedEvent priced) {
        Order order = new Order();
        order.setId(priced.orderId());
        order.setUserId(priced.userId());
        order.setRestaurantId(priced.restaurantId());
        order.setRestaurantName(priced.restaurantName());

        List<OrderItem> items = new ArrayList<>();
        if (priced.items() != null) {
            for (OrderPricedEvent.PricedItem pricedItem : priced.items()) {
                OrderItem item = new OrderItem();
                item.setId(UUID.randomUUID().toString());
                item.setMenuItemId(pricedItem.itemId());
                item.setName(pricedItem.name());
                item.setPrice(money(pricedItem.unitPrice()));
                item.setQuantity(pricedItem.quantity() == null ? 0 : pricedItem.quantity());
                item.setSubtotal(money(pricedItem.lineTotal()));
                items.add(item);
            }
        }
        order.setItems(items);

        order.setSubtotal(money(priced.itemsTotal()));
        order.setDeliveryCharge(money(priced.deliveryFee()));
        order.setTax(money(priced.tax()));
        order.setGrandTotal(money(priced.grandTotal()));
        order.setCurrency(priced.currency());
        order.setPaymentMethod(PaymentMethod.fromWire(priced.paymentMethod()));
        order.setDeliveryAddress(priced.deliveryAddress());
        order.setDeliveryLatitude(priced.deliveryLatitude());
        order.setDeliveryLongitude(priced.deliveryLongitude());
        order.setNote(priced.note());
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());
        return order;
    }

    /**
     * Moves an order from one status to the next, logging and ignoring the event when it arrives
     * out of order (a redelivery, or an event for a stage the order has already passed).
     */
    private void transition(String orderId, OrderStatus expected, OrderStatus next, String source) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("{} for unknown order {}", source, orderId);
            return;
        }
        if (order.getStatus() != expected) {
            log.warn("Ignoring {} for order {} in status {}", source, orderId, order.getStatus());
            return;
        }
        order.setStatus(next);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);
    }

    private BigDecimal money(Double value) {
        return value == null ? BigDecimal.ZERO : BigDecimal.valueOf(value);
    }

    private Order findOrderOrThrow(String id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + id));
    }

    private void requireOwnerOrAdmin(String userId, String role, Order order) {
        if (!userId.equals(order.getUserId()) && !ADMIN_ROLE.equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to access this order");
        }
    }

    private OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(), order.getUserId(), order.getRestaurantId(), order.getRestaurantName(),
                order.getItems(), order.getSubtotal(), order.getDeliveryCharge(), order.getTax(),
                order.getGrandTotal(), order.getCurrency(), order.getPaymentMethod(),
                order.getDeliveryAddress(), order.getDeliveryLatitude(), order.getDeliveryLongitude(),
                order.getNote(), order.getStatus(), order.getCreatedAt(), order.getUpdatedAt());
    }
}
