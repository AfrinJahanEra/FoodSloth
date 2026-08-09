package org.sda.userservice.dto;

import org.sda.userservice.entity.Address;

/**
 * What the client sends to create or replace an address. The id is never accepted from the
 * client - this service mints it - so it is not part of the request shape at all.
 */
public record AddressRequest(
        String label,
        String street,
        String city,
        String area,
        double latitude,
        double longitude,
        boolean defaultAddress) {

    public Address toEntity() {
        return new Address(null, label, street, city, area, latitude, longitude, defaultAddress);
    }
}
