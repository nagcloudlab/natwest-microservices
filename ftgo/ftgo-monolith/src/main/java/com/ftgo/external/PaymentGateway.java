package com.ftgo.external;

import java.math.BigDecimal;

public interface PaymentGateway {
    record PaymentResult(boolean success, String transactionId) {}
    PaymentResult charge(BigDecimal amount, String paymentMethod);
}
