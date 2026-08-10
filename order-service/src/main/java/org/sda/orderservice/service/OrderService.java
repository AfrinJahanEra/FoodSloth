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
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The order lifecycle. This service never prices anything and never calls another service:
 *
 * <ul>
 *   <li>{@code restaurant.order-priced} creates the order (PENDING_PAYMENT) and asks for payment</li>
 *   <li>{@code payment.succeeded} confirms it, {@code payment.failed} fails it</li>
 *   <li>{@code restaurant.order-accepted/rejected} moves it to PREPARING or REJECTED</li>
 *   <li>{@code delivery.started/completed} moves it to OUT_FOR_DELIVERY and DELIVERED</li>
 * </ul>
 *
 * <p>The order id is the UUID Cart Service minted at checkout, so the client can poll
 * {@code GET /orders/{orderId}} from the very first moment.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private static final String ADMIN_ROLE = "ADMIN";

    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;
    private final RabbitTemplate rabbitTemplate;
    private final MongoTemplate mongoTemplate;
    private final DhakaDeliveryValidator dhakaDeliveryValidator;

    public OrderService(OrderRepository orderRepository, OrderEventPublisher eventPublisher,
                        RabbitTemplate rabbitTemplate, MongoTemplate mongoTemplate,
                        DhakaDeliveryValidator dhakaDeliveryValidator) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.rabbitTemplate = rabbitTemplate;
        this.mongoTemplate = mongoTemplate;
        this.dhakaDeliveryValidator = dhakaDeliveryValidator;
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

        if (saved.getPaymentMethod() == PaymentMethod.CASH_ON_DELIVERY) {
            // Cash is collected at the door - there is nothing to charge now, so the order
            // confirms at once. Payment Service keeps the ledger entry PENDING and confirms
            // it when delivery.completed arrives.
            saved.setStatus(OrderStatus.CONFIRMED);
            saved.setUpdatedAt(Instant.now());
            saved = orderRepository.save(saved);
            eventPublisher.publishOrderConfirmed(saved);
        }
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
        order.setOrderNo(mintOrderNo());
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
        // PAYMENT_FAILED is accepted too: a later retry can succeed (money taken) and the
        // succeeded event is authoritative - the order must proceed, not stay failed.
        transition(orderId, OrderStatus.CONFIRMED, "payment.succeeded",
                OrderStatus.PENDING_PAYMENT, OrderStatus.PAYMENT_FAILED);
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

    /** The kitchen packed the food; the customer sees READY until a rider picks it up. */
    public void markReady(String orderId) {
        transition(orderId, OrderStatus.PREPARING, OrderStatus.READY, "restaurant.order-ready");
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
        // Normally the order is READY by now; PREPARING is accepted too in case the ready
        // event is still sitting in this queue when the rider's pickup arrives.
        transition(orderId, OrderStatus.OUT_FOR_DELIVERY, "delivery.started",
                OrderStatus.READY, OrderStatus.PREPARING);
    }

    public void markDelivered(String orderId) {
        // READY/PREPARING are accepted too: if delivery.started was ever missed, the
        // completion must still close the order instead of being ignored.
        transition(orderId, OrderStatus.DELIVERED, "delivery.completed",
                OrderStatus.OUT_FOR_DELIVERY, OrderStatus.READY, OrderStatus.PREPARING);
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

    /**
     * Orders a past order again. A fresh orderId is minted and {@code order.reorder-requested} is
     * published; Restaurant Service re-prices it at today's prices, and the usual pipeline takes
     * over from there. Returns the new orderId immediately so the client can start polling.
     */
    public String reorder(String userId, String role, String id) {
        Order original = findOrderOrThrow(id);
        requireOwnerOrAdmin(userId, role, original);
        // Dhaka-only delivery applies to reorders too: the original drop-off location must
        // still be inside Dhaka, Bangladesh.
        String outsideDhaka = dhakaDeliveryValidator.check(
                original.getDeliveryLatitude(), original.getDeliveryLongitude(), original.getDeliveryAddress());
        if (outsideDhaka != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, outsideDhaka);
        }
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
        order.setOrderNo(mintOrderNo());
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
        transition(orderId, next, source, expected);
    }

    /**
     * Moves an order to {@code next} when its current status is any of {@code expected}, logging
     * and ignoring the event when it arrives out of order (a redelivery, or an event for a stage
     * the order has already passed).
     */
    private void transition(String orderId, OrderStatus next, String source, OrderStatus... expected) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("{} for unknown order {}", source, orderId);
            return;
        }
        for (OrderStatus status : expected) {
            if (order.getStatus() == status) {
                order.setStatus(next);
                order.setUpdatedAt(Instant.now());
                orderRepository.save(order);
                return;
            }
        }
        log.warn("Ignoring {} for order {} in status {}", source, orderId, order.getStatus());
    }

    private BigDecimal money(Double value) {
        return value == null ? BigDecimal.ZERO : BigDecimal.valueOf(value);
    }

    /** Atomic sequential order number (#1, #2, ...) shown in the UI instead of the UUID. */
    private long mintOrderNo() {
        Query query = new Query(Criteria.where("_id").is("order-no"));
        Update update = new Update().inc("seq", 1);
        Document counter = mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().upsert(true).returnNew(true), Document.class, "counters");
        return ((Number) counter.get("seq")).longValue();
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
                order.getId(), order.getOrderNo(), order.getUserId(), order.getRestaurantId(), order.getRestaurantName(),
                order.getItems(), order.getSubtotal(), order.getDeliveryCharge(), order.getTax(),
                order.getGrandTotal(), order.getCurrency(), order.getPaymentMethod(),
                order.getDeliveryAddress(), order.getDeliveryLatitude(), order.getDeliveryLongitude(),
                order.getNote(), order.getStatus(), order.getCreatedAt(), order.getUpdatedAt());
    }
}
