package com.ftgo.restaurant;

import lombok.*;

import java.math.BigDecimal;

/**
 * MenuItem DTO — no longer a JPA entity!
 *
 * In the original monolith, this was an @Entity mapped to the menu_item table.
 * Now that menu data lives in the Restaurant Service (port 8081),
 * this class is just a data holder for JSON deserialization.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class MenuItem {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
}
