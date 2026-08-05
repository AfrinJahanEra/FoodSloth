package org.sda.orderservice.dto;

import java.math.BigDecimal;

public record OrderItemRequest(
        String menuItemId,
        String name,
        BigDecimal price,
        int quantity
) {
}
