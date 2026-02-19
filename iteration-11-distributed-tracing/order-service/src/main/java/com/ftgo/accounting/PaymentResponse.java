package com.ftgo.accounting;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payment DTO — no longer a JPA entity.
 * Payment data now lives in the Accounting Service (port 8083).
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @ToString
public class PaymentResponse {
    private Long id;
    private Long orderId;
    private BigDecimal amount;
    private String paymentMethod;
    private String status;
    private String transactionId;
    private LocalDateTime createdAt;
}
