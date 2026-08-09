package org.sda.restaurantservice.service;

import org.sda.restaurantservice.dto.event.CartCheckedOutEvent;
import org.sda.restaurantservice.dto.event.KitchenDecisionEvent;
import org.sda.restaurantservice.dto.event.OrderPricedEvent;
import org.sda.restaurantservice.dto.event.OrderReadyEvent;
import org.sda.restaurantservice.dto.event.OrderUnavailableEvent;
import org.sda.restaurantservice.entity.KitchenOrder;
import org.sda.restaurantservice.entity.KitchenOrderStatus;
import org.sda.restaurantservice.entity.Restaurant;
import org.sda.restaurantservice.publisher.RestaurantEventPublisher;
import org.sda.restaurantservice.repository.KitchenOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The kitchen side of Restaurant Service: intake, the kitchen screen, and the three decisions the
 * kitchen makes (accept, reject, food ready).
 *
 * <p>Every step is driven by an event in and announced by an event out. This service never calls
 * another service, and no other service calls it.
 *
 * <p>Ticket lifecycle:
 * <pre>
 *   cart.checked-out  -> priced, AWAITING_PAYMENT   -> restaurant.order-priced
 *   order.confirmed   -> QUEUED (on the screen)
 *   staff accepts     -> ACCEPTED                   -> restaurant.order-accepted
 *   staff rejects     -> REJECTED                   -> restaurant.order-rejected
 *   food ready        -> READY                      -> restaurant.order-ready
 *   order.cancelled   -> CANCELLED
 * </pre>
 */
@Service
public class KitchenService {

    private static final Logger log = LoggerFactory.getLogger(KitchenService.class);

    private final KitchenOrderRepository kitchenOrderRepository;
    private final PricingService pricingService;
    private final RestaurantService restaurantService;
    private final RestaurantEventPublisher publisher;

    public KitchenService(KitchenOrderRepository kitchenOrderRepository,
                          PricingService pricingService,
                          RestaurantService restaurantService,
                          RestaurantEventPublisher publisher) {
        this.kitchenOrderRepository = kitchenOrderRepository;
        this.pricingService = pricingService;
        this.restaurantService = restaurantService;
        this.publisher = publisher;
    }

    // ------------------------------------------------------------------
    // Driven by events
    // ------------------------------------------------------------------

    /**
     * Prices a checkout and records the ticket. Safe to run twice: the ticket id is the order id, so
     * a redelivered message rewrites the same document. Re-publishing the priced event is harmless
     * too - Order Service keys the order on the same id.
     */
    public void onCheckout(CartCheckedOutEvent checkout) {
        if (checkout.orderId() == null || checkout.orderId().isBlank()) {
            log.warn("Ignoring a checkout with no orderId - it cannot be correlated with anything");
            return;
        }

        KitchenOrder existing = kitchenOrderRepository.findById(checkout.orderId()).orElse(null);
        if (existing != null && existing.getStatus() != KitchenOrderStatus.AWAITING_PAYMENT) {
            log.info("Order {} has already moved past pricing ({}); ignoring the repeat checkout",
                    checkout.orderId(), existing.getStatus());
            return;
        }

        OrderPricedEvent priced;
        try {
            priced = pricingService.price(checkout);
        } catch (OrderUnavailableException e) {
            log.info("Cannot price order {}: {}", checkout.orderId(), e.getMessage());
            publisher.publishUnavailable(new OrderUnavailableEvent(
                    checkout.orderId(), checkout.userId(), currentRestaurantId(), e.getMessage()));
            return;
        }

        kitchenOrderRepository.save(toTicket(checkout, priced, existing));
        publisher.publishPriced(priced);
    }

    /** Payment cleared, so the ticket joins the kitchen queue (and keeps its friendly #number). */
    public void onOrderConfirmed(String orderId, Long orderNo) {
        KitchenOrder ticket = kitchenOrderRepository.findById(orderId).orElse(null);
        if (ticket == null) {
            log.warn("order.confirmed for unknown order {} - no ticket was priced here", orderId);
            return;
        }
        if (ticket.getStatus() != KitchenOrderStatus.AWAITING_PAYMENT) {
            return; // already queued or further along; a redelivery
        }
        ticket.setOrderNo(orderNo);
        ticket.setStatus(KitchenOrderStatus.QUEUED);
        save(ticket);
        log.info("Order {} is paid for and now on the kitchen queue", orderId);
    }

    /** The order is off. Nothing is published - Order Service already told everyone. */
    public void onOrderCancelled(String orderId) {
        KitchenOrder ticket = kitchenOrderRepository.findById(orderId).orElse(null);
        if (ticket == null) {
            return;
        }
        if (ticket.getStatus() == KitchenOrderStatus.READY) {
            log.warn("Order {} was cancelled after the food was ready", orderId);
        }
        ticket.setStatus(KitchenOrderStatus.CANCELLED);
        ticket.setStatusReason("Cancelled");
        save(ticket);
    }

    // ------------------------------------------------------------------
    // Driven by kitchen staff over REST
    // ------------------------------------------------------------------

    public KitchenOrder accept(String role, String orderId) {
        requireStaff(role);
        KitchenOrder ticket = require(orderId);
        requireStatus(ticket, KitchenOrderStatus.QUEUED, "accepted");

        ticket.setStatus(KitchenOrderStatus.ACCEPTED);
        ticket.setAcceptedAt(Instant.now());
        save(ticket);

        publisher.publishAccepted(new KitchenDecisionEvent(
                ticket.getId(), ticket.getUserId(), ticket.getRestaurantId(), null));
        return ticket;
    }

    public KitchenOrder reject(String role, String orderId, String reason) {
        requireStaff(role);
        KitchenOrder ticket = require(orderId);
        if (ticket.getStatus() != KitchenOrderStatus.QUEUED && ticket.getStatus() != KitchenOrderStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An order in " + ticket.getStatus() + " cannot be rejected");
        }

        String why = (reason == null || reason.isBlank()) ? "The kitchen could not take this order" : reason;
        ticket.setStatus(KitchenOrderStatus.REJECTED);
        ticket.setStatusReason(why);
        save(ticket);

        publisher.publishRejected(new KitchenDecisionEvent(
                ticket.getId(), ticket.getUserId(), ticket.getRestaurantId(), why));
        return ticket;
    }

    /**
     * The food is packed. This is the message Delivery Service is waiting for, so it carries both
     * ends of the trip - the restaurant's current location and the drop-off snapshotted at checkout.
     */
    public KitchenOrder markReady(String role, String orderId) {
        requireStaff(role);
        KitchenOrder ticket = require(orderId);
        requireStatus(ticket, KitchenOrderStatus.ACCEPTED, "marked ready");

        ticket.setStatus(KitchenOrderStatus.READY);
        ticket.setReadyAt(Instant.now());
        save(ticket);

        Restaurant restaurant = restaurantService.getRestaurant();
        publisher.publishReady(new OrderReadyEvent(
                ticket.getId(),
                ticket.getOrderNo(),
                ticket.getUserId(),
                ticket.getRestaurantId(),
                restaurant.getLatitude(),
                restaurant.getLongitude(),
                ticket.getDropLatitude(),
                ticket.getDropLongitude(),
                ticket.getDropAddressLabel(),
                ticket.getCustomerPhone()));
        return ticket;
    }

    // ------------------------------------------------------------------
    // The kitchen screen
    // ------------------------------------------------------------------

    /** What the kitchen has to act on right now: paid and waiting, plus what is already cooking. */
    public List<KitchenOrder> getActiveQueue(String role) {
        requireStaff(role);
        return kitchenOrderRepository.findByStatusInOrderByCreatedAtAsc(
                List.of(KitchenOrderStatus.QUEUED, KitchenOrderStatus.ACCEPTED));
    }

    public List<KitchenOrder> getByStatus(String role, KitchenOrderStatus status) {
        requireStaff(role);
        return kitchenOrderRepository.findByStatusOrderByCreatedAtAsc(status);
    }

    public KitchenOrder getTicket(String role, String orderId) {
        requireStaff(role);
        return require(orderId);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private KitchenOrder toTicket(CartCheckedOutEvent checkout, OrderPricedEvent priced, KitchenOrder existing) {
        KitchenOrder ticket = existing == null ? new KitchenOrder() : existing;
        ticket.setId(checkout.orderId());
        ticket.setUserId(checkout.userId());
        ticket.setRestaurantId(priced.restaurantId());
        ticket.setItems(new ArrayList<>(priced.items()));
        ticket.setItemsTotal(priced.itemsTotal());
        ticket.setDeliveryFee(priced.deliveryFee());
        ticket.setTax(priced.tax());
        ticket.setGrandTotal(priced.grandTotal());
        ticket.setCurrency(priced.currency());
        ticket.setNote(checkout.note());
        ticket.setDropAddressLabel(checkout.deliveryAddress());
        ticket.setDropLatitude(checkout.deliveryLatitude());
        ticket.setDropLongitude(checkout.deliveryLongitude());
        ticket.setCustomerPhone(checkout.contactPhone());
        ticket.setStatus(KitchenOrderStatus.AWAITING_PAYMENT);
        ticket.setUpdatedAt(Instant.now());
        return ticket;
    }

    private void save(KitchenOrder ticket) {
        ticket.setUpdatedAt(Instant.now());
        kitchenOrderRepository.save(ticket);
    }

    private KitchenOrder require(String orderId) {
        return kitchenOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No kitchen order with that id"));
    }

    private void requireStatus(KitchenOrder ticket, KitchenOrderStatus expected, String action) {
        if (ticket.getStatus() != expected) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only an order in " + expected + " can be " + action + "; this one is " + ticket.getStatus());
        }
    }

    private String currentRestaurantId() {
        return restaurantService.getRestaurant().getId();
    }

    private void requireStaff(String role) {
        if (role == null || role.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization token");
        }
        if (!"ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Restaurant staff role required");
        }
    }
}
