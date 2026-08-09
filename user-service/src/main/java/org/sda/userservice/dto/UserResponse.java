package org.sda.userservice.dto;

import org.sda.userservice.entity.Role;
import org.sda.userservice.entity.User;

import java.time.Instant;
import java.util.List;

/**
 * The only shape of a user that ever leaves this service.
 *
 * <p>Mirrors the stored document field for field except the BCrypt password hash, which is a
 * persistence detail and must never ride on the wire - not even to the user's own browser.
 */
public record UserResponse(
        String id,
        String email,
        String phone,
        String name,
        String photo,
        Role role,
        String vehicleType,
        String licenseNumber,
        String restaurantId,
        List<AddressView> addresses,
        List<String> foodPreferences,
        List<String> dietaryTags,
        String defaultPaymentMethod,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getPhone(),
                user.getName(),
                user.getPhoto(),
                user.getRole(),
                user.getVehicleType(),
                user.getLicenseNumber(),
                user.getRestaurantId(),
                user.getAddresses().stream().map(AddressView::from).toList(),
                user.getFoodPreferences(),
                user.getDietaryTags(),
                user.getDefaultPaymentMethod(),
                user.getCreatedAt());
    }

    /** One saved address, exactly as the checkout address picker renders it. */
    public record AddressView(
            String id,
            String label,
            String street,
            String city,
            String area,
            double latitude,
            double longitude,
            boolean defaultAddress) {

        public static AddressView from(org.sda.userservice.entity.Address address) {
            return new AddressView(address.getId(), address.getLabel(), address.getStreet(), address.getCity(),
                    address.getArea(), address.getLatitude(), address.getLongitude(), address.isDefaultAddress());
        }
    }
}
