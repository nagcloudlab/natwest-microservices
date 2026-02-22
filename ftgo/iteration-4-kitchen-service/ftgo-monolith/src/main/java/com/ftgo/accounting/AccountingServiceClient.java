package com.ftgo.accounting;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Anti-Corruption Layer + Circuit Breaker + Timeout for the Accounting Service.
 *
 * This class demonstrates 2 of the 6 MicroProfile Fault Tolerance patterns:
 *
 * | # | Pattern          | Where Applied | Why It Fits                                  |
 * |---|------------------|---------------|----------------------------------------------|
 * | 1 | @Timeout         | Constructor   | Prevent hanging on slow accounting-service   |
 * | 4 | @CircuitBreaker  | authorizePayment() | Fail fast when payment service is down |
 *
 * Why NO @Retry on authorizePayment()?
 *   POST /payments/authorize is NOT idempotent — retrying could DOUBLE-CHARGE the customer!
 *   Compare to RestaurantServiceClient.getRestaurant() where @Retry IS safe (idempotent GET).
 *
 * Why NO @Bulkhead on authorizePayment()?
 *   Payment is on the critical order path. Rejecting payments due to concurrency limits
 *   would mean losing revenue. Better to let all payment requests through and rely on
 *   the circuit breaker for failure protection.
 *
 * Why NO @Fallback with graceful degradation?
 *   Payment is critical — we can't approve an order without confirmed payment.
 *   The fallback THROWS an exception, rejecting the order. Compare to
 *   RestaurantServiceClient.getAllRestaurants() where the fallback returns an empty list.
 */
@Slf4j
@Service("accountingService")
public class AccountingServiceClient {

    private final RestTemplate restTemplate;
    private final String accountingServiceUrl;

    /**
     * Pattern 1: @Timeout (MicroProfile) → RestTemplate connectTimeout + readTimeout (Spring)
     *
     * Without timeouts, a slow accounting-service blocks the caller's thread for the
     * default TCP timeout (~30s). With payment processing (which involves external
     * gateways like Stripe), response times can be unpredictable.
     *
     * connectTimeout: max time to establish a TCP connection (2s)
     * readTimeout: max time to wait for response data after connection is established (3s)
     */
    public AccountingServiceClient(
            @Value("${accounting-service.url:http://localhost:8083}") String accountingServiceUrl,
            @Value("${accounting-service.connect-timeout:2000}") int connectTimeout,
            @Value("${accounting-service.read-timeout:3000}") int readTimeout) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        this.restTemplate = new RestTemplate(factory);
        this.accountingServiceUrl = accountingServiceUrl;

        log.info("AccountingServiceClient initialized: url={}, connectTimeout={}ms, readTimeout={}ms",
                accountingServiceUrl, connectTimeout, readTimeout);
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
                accountingServiceUrl + "/api/payments/authorize",
                request,
                PaymentResponse.class);
    }

    /**
     * Fallback when accounting-service is down or circuit is OPEN.
     *
     * Unlike restaurant data (where we can return an empty list), payment failure
     * is critical. We reject the order rather than accepting it without payment.
     *
     * In a real system, you might:
     * - Queue the payment for retry (with idempotency key)
     * - Accept the order with status PAYMENT_PENDING
     * - Use a saga to reconcile later
     */
    public PaymentResponse authorizePaymentFallback(Long orderId, BigDecimal amount, String paymentMethod, Throwable t) {
        log.error(">>> CIRCUIT BREAKER: Accounting service unavailable for order #{}. Reason: {}", orderId, t.getMessage());
        throw new RuntimeException("Payment service is currently unavailable. Please try again later. (Circuit Breaker active)");
    }
}
