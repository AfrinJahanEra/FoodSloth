package org.sda.orderservice.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {
    private String id;
    private String menuItemId;
    private String name;
    private BigDecimal price;
    private int quantity;
    private BigDecimal subtotal;
}
