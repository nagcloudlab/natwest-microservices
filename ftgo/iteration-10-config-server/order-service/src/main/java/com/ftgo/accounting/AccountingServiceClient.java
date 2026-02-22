package com.ftgo.accounting;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Anti-Corruption Layer + Circuit Breaker for the Accounting Service.
 *
 * Iteration 9 change: RestTemplate is now injected as a @LoadBalanced bean.
 * URLs use Eureka service names (http://accounting-service/...) — no hardcoded host:port.
 *
 * Why NO @Retry on authorizePayment()?
 *   POST /payments/authorize is NOT idempotent — retrying could DOUBLE-CHARGE the customer!
 */
@Slf4j
@Service("accountingService")
public class AccountingServiceClient {

    private final RestTemplate restTemplate;

    public AccountingServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        log.info("AccountingServiceClient initialized with @LoadBalanced RestTemplate (Eureka discovery)");
    }

    @CircuitBreaker(name = "accountingService", fallbackMethod = "authorizePaymentFallback")
    public PaymentResponse authorizePayment(Long orderId, BigDecimal amount, String paymentMethod) {
        log.info(">>> Calling accounting-service to authorize payment for order #{}", orderId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "orderId", orderId,
                "amount", amount,
                "paymentMethod", paymentMethod
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        return restTemplate.postForObject(
                "http://accounting-service/api/payments/authorize",
                request,
                PaymentResponse.class);
    }

    public PaymentResponse authorizePaymentFallback(Long orderId, BigDecimal amount, String paymentMethod, Throwable t) {
        log.error(">>> CIRCUIT BREAKER: Accounting service unavailable for order #{}. Reason: {}", orderId, t.getMessage());
        throw new RuntimeException("Payment service is currently unavailable. Please try again later. (Circuit Breaker active)");
    }
}
