package com.service.cart_service.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartItem {
    private String itemId;
    private String name;
    private String photo;
    private double price;
    private int quantity;
    private double lineTotal;
}
