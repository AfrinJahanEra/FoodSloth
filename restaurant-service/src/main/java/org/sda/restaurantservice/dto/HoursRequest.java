package org.sda.restaurantservice.dto;

import org.sda.restaurantservice.entity.OperatingHours;

/** One row of the weekly opening schedule as the admin editor submits it. */
public record HoursRequest(String day, String openTime, String closeTime) {

    public OperatingHours toEntity() {
        OperatingHours hours = new OperatingHours();
        hours.setDay(day);
        hours.setOpenTime(openTime);
        hours.setCloseTime(closeTime);
        return hours;
    }
}
