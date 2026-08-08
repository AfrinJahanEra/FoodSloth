package org.sda.restaurantservice.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MenuItem {
    private String id;
    private String name;
    private String description;
    private String category;
    private Double price;
    private String photo;
    private Boolean available = Boolean.TRUE;
}
