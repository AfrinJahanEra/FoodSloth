package org.sda.userservice.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Address {
    private String id;
    private String label;
    private String street;
    private String city;
    private String area;
    private boolean defaultAddress;
}
