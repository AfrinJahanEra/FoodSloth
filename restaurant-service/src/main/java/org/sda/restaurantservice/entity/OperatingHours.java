package org.sda.restaurantservice.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OperatingHours {
    private String day;
    private String openTime;
    private String closeTime;
}
