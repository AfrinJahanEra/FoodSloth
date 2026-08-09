package org.sda.restaurantservice.service;

import org.sda.restaurantservice.dto.event.CartCheckedOutEvent;
import org.sda.restaurantservice.dto.event.OrderPricedEvent;
import org.sda.restaurantservice.dto.event.PricedItem;
import org.sda.restaurantservice.entity.MenuItem;
import org.sda.restaurantservice.entity.Restaurant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns a checkout into money. This is the one place in the platform where a price is decided, which
 * is the whole reason Cart Service can get away with storing nothing but item ids and quantities.
 *
 * <p>The fee and tax rules are configuration ({@code restaurant.*} in application.yml), not
 * hard-coded numbers, so they can be changed without a code change.
 */
@Service
public class PricingService {

    private final RestaurantService restaurantService;
    private final double deliveryFee;
    private final double taxRate;
    private final String currency;

    public PricingService(RestaurantService restaurantService,
                          @Value("${restaurant.delivery-fee}") double deliveryFee,
                          @Value("${restaurant.tax-rate}") double taxRate,
                          @Value("${restaurant.currency}") String currency) {
        this.restaurantService = restaurantService;
        this.deliveryFee = deliveryFee;
        this.taxRate = taxRate;
        this.currency = currency;
    }

    /**
     * Prices every line against today's menu and returns the event Order Service is waiting for.
     *
     * @throws OrderUnavailableException if the order cannot be fulfilled at all
     */
    public OrderPricedEvent price(CartCheckedOutEvent checkout) {
        if (checkout.items() == null || checkout.items().isEmpty()) {
            throw new OrderUnavailableException("The cart was empty");
        }

        Restaurant restaurant = restaurantService.getRestaurant();
        if (!restaurant.isOpen()) {
            throw new OrderUnavailableException("The restaurant is closed right now");
        }

        Map<String, MenuItem> menu = restaurant.getMenu().stream()
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(MenuItem::getId, Function.identity(), (first, second) -> first));

        List<PricedItem> priced = new ArrayList<>();
        double itemsTotal = 0.0;

        for (CartCheckedOutEvent.CheckoutItem line : checkout.items()) {
            MenuItem menuItem = menu.get(line.itemId());
            if (menuItem == null) {
                throw new OrderUnavailableException("An item in your cart is no longer on the menu");
            }
            if (!Boolean.TRUE.equals(menuItem.getAvailable())) {
                throw new OrderUnavailableException(menuItem.getName() + " is sold out");
            }
            if (line.quantity() == null || line.quantity() < 1) {
                throw new OrderUnavailableException("Invalid quantity for " + menuItem.getName());
            }

            double unitPrice = menuItem.getPrice() == null ? 0.0 : menuItem.getPrice();
            double lineTotal = round(unitPrice * line.quantity());
            priced.add(new PricedItem(menuItem.getId(), menuItem.getName(), unitPrice, line.quantity(), lineTotal));
            itemsTotal += lineTotal;
        }

        itemsTotal = round(itemsTotal);
        double tax = round(itemsTotal * taxRate);
        double grandTotal = round(itemsTotal + deliveryFee + tax);

        return new OrderPricedEvent(
                checkout.orderId(),
                checkout.userId(),
                restaurant.getId(),
                restaurant.getName(),
                restaurant.getLatitude(),
                restaurant.getLongitude(),
                priced,
                itemsTotal,
                deliveryFee,
                tax,
                grandTotal,
                currency,
                checkout.deliveryAddress(),
                checkout.deliveryLatitude(),
                checkout.deliveryLongitude(),
                checkout.paymentMethod(),
                checkout.note());
    }

    /** Money is kept to two decimals so the total always matches the sum of the lines shown. */
    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
