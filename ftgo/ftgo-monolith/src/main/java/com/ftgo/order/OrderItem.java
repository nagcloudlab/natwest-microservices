package com.ftgo.order;

import jakarta.persistence.Embeddable;
import lombok.*;
import java.math.BigDecimal;

@Embeddable
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class OrderItem {
    private String menuItemName;
    private BigDecimal price;
    private int quantity;
}
