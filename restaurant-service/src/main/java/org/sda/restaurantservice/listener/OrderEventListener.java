package org.sda.restaurantservice.listener;

import org.sda.restaurantservice.dto.event.CartCheckedOutEvent;
import org.sda.restaurantservice.dto.event.OrderCancelledEvent;
import org.sda.restaurantservice.dto.event.OrderConfirmedEvent;
import org.sda.restaurantservice.messaging.Constants;
import org.sda.restaurantservice.service.KitchenService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * The only way work enters this service. No other service calls Restaurant Service over HTTP.
 *
 * <p>If this service is down, checkouts pile up in {@code restaurant.order-intake.queue} and are
 * priced the moment it comes back - the customer's order is never lost and the cart never errors.
 */
@Component
public class OrderEventListener {

    private final KitchenService kitchenService;

    public OrderEventListener(KitchenService kitchenService) {
        this.kitchenService = kitchenService;
    }

    /**
     * Serves both {@code cart.checked-out} and {@code order.reorder-requested}: a reorder is just
     * "price these items again", so it needs no separate handling.
     */
    @RabbitListener(queues = Constants.QUEUE_ORDER_INTAKE)
    public void onCheckout(CartCheckedOutEvent event) {
        kitchenService.onCheckout(event);
    }

    @RabbitListener(queues = Constants.QUEUE_ORDER_CONFIRMED)
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        kitchenService.onOrderConfirmed(event.orderId());
    }

    @RabbitListener(queues = Constants.QUEUE_ORDER_CANCELLED)
    public void onOrderCancelled(OrderCancelledEvent event) {
        kitchenService.onOrderCancelled(event.orderId());
    }
}
