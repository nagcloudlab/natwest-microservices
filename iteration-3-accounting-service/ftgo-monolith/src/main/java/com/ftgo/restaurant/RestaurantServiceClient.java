package com.ftgo.restaurant;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Strangler Fig client for Restaurant Service — now with ALL fault tolerance patterns!
 *
 * This class demonstrates 5 of the 6 MicroProfile Fault Tolerance patterns:
 *
 * | # | Pattern          | Where Applied        | Why It Fits                                    |
 * |---|------------------|----------------------|------------------------------------------------|
 * | 1 | @Timeout         | Constructor          | Prevent hanging on slow restaurant-service     |
 * | 2 | @Retry           | getRestaurant()      | Idempotent GET — retry transient failures      |
 * | 3 | @Fallback        | getAllRestaurants()   | Non-critical browsing — degrade gracefully     |
 * | 4 | @CircuitBreaker  | All methods          | Fail fast when restaurant-service is down      |
 * | 5 | @Bulkhead        | getAllRestaurants()   | High-traffic browsing — limit concurrency      |
 *
 * Resilience4j annotation ordering (outermost → innermost):
 *   Retry → CircuitBreaker → RateLimiter → TimeLimiter → Bulkhead → Method
 *
 * This means:
 *   - @Retry wraps @CircuitBreaker — retries only happen if circuit is CLOSED
 *   - @CircuitBreaker wraps @Bulkhead — circuit counts bulkhead rejections as failures
 *   - @Bulkhead is closest to the method — limits actual concurrent executions
 */
@Slf4j
@Service("restaurantService")
public class RestaurantServiceClient {

    private final RestTemplate restTemplate;
    private final String restaurantServiceUrl;

    /**
     * Pattern 1: @Timeout (MicroProfile) → RestTemplate connectTimeout + readTimeout (Spring)
     *
     * Without timeouts, a slow/hanging restaurant-service blocks the caller's thread
     * for the default TCP timeout (~30 seconds), potentially exhausting the thread pool.
     *
     * connectTimeout: max time to establish a TCP connection (2s)
     * readTimeout: max time to wait for response data after connection is established (3s)
     */
    public RestaurantServiceClient(
            @Value("${restaurant-service.url:http://localhost:8081}") String restaurantServiceUrl,
            @Value("${restaurant-service.connect-timeout:2000}") int connectTimeout,
            @Value("${restaurant-service.read-timeout:3000}") int readTimeout) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        this.restTemplate = new RestTemplate(factory);
        this.restaurantServiceUrl = restaurantServiceUrl;

        log.info("RestaurantServiceClient initialized: url={}, connectTimeout={}ms, readTimeout={}ms",
                restaurantServiceUrl, connectTimeout, readTimeout);
    }

    /**
     * Pattern 2: @Retry — automatically retry transient failures.
     *
     * Why retry HERE but not on authorizePayment()?
     * - GET /restaurants/{id} is IDEMPOTENT — calling it twice returns the same result
     * - POST /payments/authorize is NOT idempotent — retrying could double-charge!
     *
     * Config: max-attempts=3, wait=500ms, exponential backoff (500ms → 1000ms → 2000ms)
     *
     * Pattern 4: @CircuitBreaker — fail fast when service is down.
     * If the circuit is OPEN, the retry won't even attempt — it goes straight to fallback.
     */
    @Retry(name = "restaurantService", fallbackMethod = "getRestaurantFallback")
    @CircuitBreaker(name = "restaurantService", fallbackMethod = "getRestaurantFallback")
    public Restaurant getRestaurant(Long id) {
        log.debug("Fetching restaurant #{} from restaurant-service", id);
        return restTemplate.getForObject(
                restaurantServiceUrl + "/api/restaurants/{id}",
                Restaurant.class,
                id);
    }

    /**
     * Patterns 3+4+5: @Fallback + @CircuitBreaker + @Bulkhead
     *
     * This method is a showcase of layered resilience:
     *
     * @Bulkhead (Pattern 5): Limits concurrent calls to 5.
     *   getAllRestaurants() is a high-traffic browsing endpoint — every consumer hits it.
     *   Without a bulkhead, a slow restaurant-service could consume ALL servlet threads.
     *   The bulkhead isolates this method so other endpoints (ordering, kitchen) still work.
     *
     * @CircuitBreaker (Pattern 4): Fails fast when service is consistently down.
     *   After 3+ failures in a window of 5, the circuit opens for 15 seconds.
     *
     * @Fallback (Pattern 3): Returns an empty list instead of a 500 error.
     *   Browsing restaurants is non-critical — showing "no restaurants available" is
     *   better than crashing the page. Compare to getMenuItemsByIds() where we CAN'T
     *   fall back (can't create an order without prices).
     */
    @Bulkhead(name = "restaurantService", fallbackMethod = "getAllRestaurantsFallback")
    @CircuitBreaker(name = "restaurantService", fallbackMethod = "getAllRestaurantsFallback")
    public List<Restaurant> getAllRestaurants() {
        log.debug("Fetching all restaurants from restaurant-service");
        return restTemplate.exchange(
                restaurantServiceUrl + "/api/restaurants",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<Restaurant>>() {}
        ).getBody();
    }

    /**
     * Pattern 4: @CircuitBreaker — critical path, NO graceful fallback.
     *
     * Unlike getAllRestaurants(), we CANNOT return a fallback here.
     * If we can't fetch menu items, we can't calculate the order total.
     * Accepting an order without knowing the prices would be a business logic error.
     *
     * The fallback THROWS an exception — this is intentional!
     * Fallback strategies differ by criticality:
     *   - getAllRestaurants() → return empty list (browsing is optional)
     *   - getMenuItemsByIds() → throw exception (can't order without prices)
     *   - authorizePayment() → throw exception (can't approve without payment)
     */
    @CircuitBreaker(name = "restaurantService", fallbackMethod = "getMenuItemsByIdsFallback")
    public List<MenuItem> getMenuItemsByIds(List<Long> ids) {
        log.debug("Fetching menu items {} from restaurant-service", ids);
        String idsParam = ids.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        return restTemplate.exchange(
                restaurantServiceUrl + "/api/restaurants/menu-items?ids={ids}",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<MenuItem>>() {},
                idsParam
        ).getBody();
    }

    // ======================== FALLBACK METHODS ========================

    /**
     * Fallback for getRestaurant() — can't degrade, must throw.
     * We need restaurant details to create an order.
     */
    public Restaurant getRestaurantFallback(Long id, Throwable t) {
        log.error(">>> FALLBACK: Cannot fetch restaurant #{}. Reason: {}", id, t.getMessage());
        throw new RuntimeException(
                "Restaurant service is currently unavailable. Please try again later.");
    }

    /**
     * Fallback for getAllRestaurants() — GRACEFUL DEGRADATION.
     * Return empty list instead of 500 error. The UI shows "no restaurants available".
     */
    public List<Restaurant> getAllRestaurantsFallback(Throwable t) {
        log.warn(">>> FALLBACK: Restaurant service unavailable, returning empty list. Reason: {}", t.getMessage());
        return List.of();
    }

    /**
     * Fallback for getMenuItemsByIds() — can't degrade, must throw.
     * We need menu items to calculate order totals.
     */
    public List<MenuItem> getMenuItemsByIdsFallback(List<Long> ids, Throwable t) {
        log.error(">>> FALLBACK: Cannot fetch menu items {}. Reason: {}", ids, t.getMessage());
        throw new RuntimeException(
                "Restaurant service is currently unavailable. Cannot retrieve menu items for order.");
    }
}
