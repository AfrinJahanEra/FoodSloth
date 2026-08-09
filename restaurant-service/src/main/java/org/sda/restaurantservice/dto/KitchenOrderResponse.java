package org.sda.restaurantservice.dto;

import org.sda.restaurantservice.dto.event.PricedItem;
import org.sda.restaurantservice.entity.KitchenOrder;
import org.sda.restaurantservice.entity.KitchenOrderStatus;

import java.time.Instant;
import java.util.List;

/**
 * One kitchen ticket as the kitchen screen renders it: what to cook, the price snapshot this
 * service produced, and where the food is going. {@code updatedAt} is internal bookkeeping.
 */
public record KitchenOrderResponse(
        String id,
        String userId,
        String restaurantId,
        List<PricedItem> items,
        double itemsTotal,
        double deliveryFee,
        double tax,
        double grandTotal,
        String currency,
        KitchenOrderStatus status,
        String statusReason,
        String note,
        String dropAddressLabel,
        Double dropLatitude,
        Double dropLongitude,
        Instant createdAt,
        Instant acceptedAt,
        Instant readyAt) {

    public static KitchenOrderResponse from(KitchenOrder order) {
        return new KitchenOrderResponse(order.getId(), order.getUserId(), order.getRestaurantId(), order.getItems(),
                order.getItemsTotal(), order.getDeliveryFee(), order.getTax(), order.getGrandTotal(),
                order.getCurrency(), order.getStatus(), order.getStatusReason(), order.getNote(),
                order.getDropAddressLabel(), order.getDropLatitude(), order.getDropLongitude(),
                order.getCreatedAt(), order.getAcceptedAt(), order.getReadyAt());
    }
}
