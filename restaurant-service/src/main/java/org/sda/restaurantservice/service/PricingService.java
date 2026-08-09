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

    /** Straight-line distance is stretched by this factor - roads are never straight. */
    private static final double ROAD_FACTOR = 1.3;
    private static final double EARTH_RADIUS_KM = 6371.0;

    private final RestaurantService restaurantService;
    private final double baseDeliveryFee;
    private final double deliveryFeePerKm;
    private final double maxDeliveryFee;
    private final double taxRate;
    private final String currency;

    public PricingService(RestaurantService restaurantService,
                          @Value("${restaurant.delivery-fee}") double baseDeliveryFee,
                          @Value("${restaurant.delivery-fee-per-km}") double deliveryFeePerKm,
                          @Value("${restaurant.delivery-fee-max}") double maxDeliveryFee,
                          @Value("${restaurant.tax-rate}") double taxRate,
                          @Value("${restaurant.currency}") String currency) {
        this.restaurantService = restaurantService;
        this.baseDeliveryFee = baseDeliveryFee;
        this.deliveryFeePerKm = deliveryFeePerKm;
        this.maxDeliveryFee = maxDeliveryFee;
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
        double fee = deliveryFeeFor(restaurant, checkout);
        double tax = round(itemsTotal * taxRate);
        double grandTotal = round(itemsTotal + fee + tax);

        return new OrderPricedEvent(
                checkout.orderId(),
                checkout.userId(),
                restaurant.getId(),
                restaurant.getName(),
                restaurant.getLatitude(),
                restaurant.getLongitude(),
                priced,
                itemsTotal,
                fee,
                tax,
                grandTotal,
                currency,
                checkout.deliveryAddress(),
                checkout.deliveryLatitude(),
                checkout.deliveryLongitude(),
                checkout.paymentMethod(),
                checkout.note());
    }

    /**
     * Like a real delivery app: a base (flag-fall) fee plus a per-km rate over the distance between
     * the restaurant and the drop-off. When either location has no coordinates the base fee applies,
     * so an address saved without a map pin still gets a sensible price.
     */
    private double deliveryFeeFor(Restaurant restaurant, CartCheckedOutEvent checkout) {
        if (!hasLocation(restaurant.getLatitude(), restaurant.getLongitude())
                || !hasLocation(checkout.deliveryLatitude(), checkout.deliveryLongitude())) {
            return baseDeliveryFee;
        }
        double roadKm = haversineKm(restaurant.getLatitude(), restaurant.getLongitude(),
                checkout.deliveryLatitude(), checkout.deliveryLongitude()) * ROAD_FACTOR;
        return Math.min(maxDeliveryFee, round(baseDeliveryFee + roadKm * deliveryFeePerKm));
    }

    /** null or (0,0) means "no coordinates were captured" - not a real place. */
    private boolean hasLocation(Double lat, Double lon) {
        return lat != null && lon != null && (lat != 0.0 || lon != 0.0);
    }

    /** Great-circle distance in kilometres (haversine). */
    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
    }

    /** Money is kept to two decimals so the total always matches the sum of the lines shown. */
    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
