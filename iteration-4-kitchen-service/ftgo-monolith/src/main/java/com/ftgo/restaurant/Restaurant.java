package com.ftgo.restaurant;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Restaurant DTO — no longer a JPA entity!
 *
 * In the original monolith, this was an @Entity mapped to the restaurant table.
 * Now that restaurant data lives in the Restaurant Service (port 8081),
 * this class is just a data holder for JSON deserialization.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Restaurant {
    private Long id;
    private String name;
    private String address;
    private String phone;
    private boolean isOpen;
    private List<MenuItem> menuItems = new ArrayList<>();
}
