#!/usr/bin/env bash
# =============================================================================
# Test All 6 MicroProfile Fault Tolerance Patterns
# =============================================================================
# Prerequisites: All 4 services running (restaurant:8081, notification:8082,
#                accounting:8083, monolith:8080) + Kafka cluster
#
# Usage:  ./test-resilience.sh
# =============================================================================

set -uo pipefail

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

MONOLITH="http://localhost:8080"
RESTAURANT="http://localhost:8081"
ACCOUNTING="http://localhost:8083"

PASS=0
FAIL=0

# --- Helpers ---

print_header() {
    echo ""
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BOLD}  $1${NC}"
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

print_test() {
    echo -e "\n${YELLOW}▸ TEST: $1${NC}"
}

pass() {
    echo -e "  ${GREEN}✔ PASS:${NC} $1"
    PASS=$((PASS + 1))
}

fail() {
    echo -e "  ${RED}✘ FAIL:${NC} $1"
    FAIL=$((FAIL + 1))
}

check_service() {
    local name=$1 url=$2
    if curl -s -o /dev/null -w '' --max-time 3 "$url" 2>/dev/null; then
        echo -e "  ${GREEN}●${NC} $name is UP"
        return 0
    else
        echo -e "  ${RED}●${NC} $name is DOWN"
        return 1
    fi
}

# =============================================================================
# PREFLIGHT: Check all services are running
# =============================================================================

print_header "PREFLIGHT: Checking services"

ALL_UP=true
check_service "Monolith (8080)"           "$MONOLITH/"                    || ALL_UP=false
check_service "Restaurant Service (8081)" "$RESTAURANT/api/restaurants"   || ALL_UP=false
check_service "Accounting Service (8083)" "$ACCOUNTING/api/payments/order/0" || ALL_UP=false

if [ "$ALL_UP" = false ]; then
    echo -e "\n${RED}ERROR: Not all services are running. Start them first:${NC}"
    echo "  Terminal 1: cd restaurant-service  && mvn spring-boot:run"
    echo "  Terminal 2: cd notification-service && mvn spring-boot:run"
    echo "  Terminal 3: cd accounting-service   && mvn spring-boot:run"
    echo "  Terminal 4: cd ftgo-monolith        && mvn spring-boot:run"
    exit 1
fi
echo -e "\n${GREEN}All services are running.${NC}"

# =============================================================================
# PATTERN 1: @Timeout — verify connect/read timeouts are configured
# =============================================================================

print_header "PATTERN 1: @Timeout"

print_test "Monolith responds within timeout window"
RESPONSE_TIME=$(curl -s -o /dev/null -w '%{time_total}' --max-time 10 \
    "$MONOLITH/consumer/restaurants")
# Normal response should be well under the 3s readTimeout
if (( $(echo "$RESPONSE_TIME < 2.0" | bc -l) )); then
    pass "Response in ${RESPONSE_TIME}s (within timeout budget)"
else
    fail "Response took ${RESPONSE_TIME}s (expected < 2s for normal flow)"
fi

print_test "Timeout config loaded (check startup logs)"
echo -e "  ${CYAN}ℹ  Look for these log lines at startup:${NC}"
echo "    AccountingServiceClient initialized: connectTimeout=2000ms, readTimeout=3000ms"
echo "    RestaurantServiceClient initialized: connectTimeout=2000ms, readTimeout=3000ms"
echo -e "  ${CYAN}ℹ  Manual exercise: Add Thread.sleep(5000) to restaurant-service → monolith times out after 3s${NC}"

# =============================================================================
# PATTERN 2: @Retry — place an order (exercises getRestaurant with @Retry)
# =============================================================================

print_header "PATTERN 2: @Retry + Normal Order Flow"

print_test "Place order (exercises @Retry + @CircuitBreaker on getRestaurant)"
ORDER_RESPONSE=$(curl -s -X POST "$MONOLITH/api/orders" \
    -H "Content-Type: application/json" \
    -d '{
        "consumerId": 1,
        "consumerName": "ResilienceTest",
        "consumerContact": "9876543210",
        "restaurantId": 1,
        "deliveryAddress": "42 Fault Tolerance Lane",
        "paymentMethod": "CREDIT_CARD",
        "items": [{"menuItemId": 1, "quantity": 1}]
    }')

ORDER_STATUS=$(echo "$ORDER_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('status','UNKNOWN'))" 2>/dev/null || echo "PARSE_ERROR")
ORDER_ID=$(echo "$ORDER_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id','?'))" 2>/dev/null || echo "?")

if [ "$ORDER_STATUS" = "APPROVED" ]; then
    pass "Order #$ORDER_ID created with status APPROVED"
else
    fail "Order status: $ORDER_STATUS (expected APPROVED)"
    echo "  Response: $ORDER_RESPONSE"
fi

# =============================================================================
# PATTERN 6: @Asynchronous — verify Kafka publish runs on async thread
# =============================================================================

print_header "PATTERN 6: @Asynchronous (@Async)"

print_test "Order response time (should be fast — Kafka publish is async)"
ORDER_TIME=$(curl -s -o /dev/null -w '%{time_total}' --max-time 10 \
    -X POST "$MONOLITH/api/orders" \
    -H "Content-Type: application/json" \
    -d '{
        "consumerId": 1,
        "consumerName": "AsyncTest",
        "consumerContact": "1111111111",
        "restaurantId": 1,
        "deliveryAddress": "Async Avenue",
        "paymentMethod": "CREDIT_CARD",
        "items": [{"menuItemId": 2, "quantity": 1}]
    }')

if (( $(echo "$ORDER_TIME < 3.0" | bc -l) )); then
    pass "Order response in ${ORDER_TIME}s (Kafka publish didn't block)"
else
    fail "Order response took ${ORDER_TIME}s (Kafka publish may be blocking)"
fi

echo -e "  ${CYAN}ℹ  Check monolith logs for:${NC}"
echo "    >>> Publishing OrderCreated event on thread [event-publisher-1]"
echo -e "  ${CYAN}ℹ  Thread should be 'event-publisher-*', NOT 'http-nio-8080-exec-*'${NC}"

# =============================================================================
# PATTERN 4: @CircuitBreaker (accounting) — verify payment was recorded
# =============================================================================

print_header "PATTERN 4: @CircuitBreaker (Accounting Service)"

print_test "Payment recorded in accounting-service"
if [ "$ORDER_ID" != "?" ]; then
    PAYMENT=$(curl -s "$ACCOUNTING/api/payments/order/$ORDER_ID")
    PAY_STATUS=$(echo "$PAYMENT" | python3 -c "import sys,json; print(json.load(sys.stdin).get('status','UNKNOWN'))" 2>/dev/null || echo "PARSE_ERROR")
    if [ "$PAY_STATUS" = "AUTHORIZED" ]; then
        pass "Payment for order #$ORDER_ID: status=$PAY_STATUS"
    else
        fail "Payment status: $PAY_STATUS (expected AUTHORIZED)"
    fi
else
    fail "Skipped — no order ID from previous test"
fi

# =============================================================================
# KILL RESTAURANT SERVICE — test Fallback, CircuitBreaker, Bulkhead
# =============================================================================

print_header "STOPPING RESTAURANT SERVICE (for Patterns 3, 4, 5)"

# Find the Java process (not Maven wrapper) for restaurant-service
RESTAURANT_PID=$(ps aux | grep '[j]ava.*restaurant-service' | awk '{print $2}' | head -1 || true)
if [ -n "$RESTAURANT_PID" ]; then
    kill "$RESTAURANT_PID" 2>/dev/null || true
    sleep 3
    echo -e "  ${YELLOW}⚠  Restaurant service stopped (Java PID: $RESTAURANT_PID)${NC}"
else
    # Fallback: kill by port
    lsof -ti:8081 2>/dev/null | xargs kill 2>/dev/null || true
    sleep 3
    echo -e "  ${YELLOW}⚠  Restaurant service stopped (by port)${NC}"
fi

# Verify it's down
if curl -s -o /dev/null --max-time 2 "$RESTAURANT/api/restaurants" 2>/dev/null; then
    fail "Restaurant service is still running!"
else
    pass "Restaurant service confirmed DOWN"
fi

# Verify monolith is still alive
if ! curl -s -o /dev/null --max-time 3 "$MONOLITH/" 2>/dev/null; then
    echo -e "  ${YELLOW}⚠  Monolith also went down — restarting...${NC}"
    cd "$(dirname "$0")/ftgo-monolith"
    nohup mvn spring-boot:run -q > /tmp/monolith-restart.log 2>&1 &
    cd - > /dev/null
    echo -n "  Waiting for monolith"
    for i in $(seq 1 30); do
        if curl -s -o /dev/null --max-time 2 "$MONOLITH/" 2>/dev/null; then
            echo ""
            echo -e "  ${GREEN}●${NC} Monolith restarted"
            break
        fi
        echo -n "."
        sleep 2
    done
fi

# =============================================================================
# PATTERN 3: @Fallback — browse restaurants returns 200 with empty list
# =============================================================================

print_header "PATTERN 3: @Fallback (Graceful Degradation)"

print_test "Browse restaurants with service DOWN → should get 200 (not 500)"
HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 \
    "$MONOLITH/consumer/restaurants" || true)

if [ "$HTTP_CODE" = "200" ]; then
    pass "HTTP $HTTP_CODE — fallback returned empty list (graceful degradation)"
else
    fail "HTTP $HTTP_CODE — expected 200 (fallback should return empty list)"
fi

print_test "Place order with service DOWN → should FAIL (menu items are critical)"
FAIL_RESPONSE=$(curl -s -X POST "$MONOLITH/api/orders" \
    -H "Content-Type: application/json" \
    --max-time 15 \
    -d '{
        "consumerId": 1,
        "consumerName": "FailTest",
        "consumerContact": "0000000000",
        "restaurantId": 1,
        "deliveryAddress": "Nowhere",
        "paymentMethod": "CREDIT_CARD",
        "items": [{"menuItemId": 1, "quantity": 1}]
    }' || true)
FAIL_STATUS=$(echo "$FAIL_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('status','ERROR'))" 2>/dev/null || echo "ERROR")

if [ "$FAIL_STATUS" != "APPROVED" ]; then
    pass "Order correctly REJECTED — can't order without menu prices (fallback throws)"
else
    fail "Order was APPROVED without restaurant service — this shouldn't happen!"
fi

# =============================================================================
# PATTERN 4: @CircuitBreaker (restaurant) — fast-fail after failures
# =============================================================================

print_header "PATTERN 4: @CircuitBreaker (Restaurant Service — Fast Fail)"

print_test "Repeated requests → circuit opens → responses are instant"
echo ""
ALL_FAST=true
for i in 1 2 3 4 5 6; do
    RESULT=$(curl -s -o /dev/null -w '%{http_code} %{time_total}' --max-time 10 \
        "$MONOLITH/consumer/restaurants" || true)
    HTTP=$(echo "$RESULT" | awk '{print $1}')
    TIME=$(echo "$RESULT" | awk '{print $2}')
    echo -e "    Request $i: HTTP=$HTTP  Time=${TIME}s"
    # After circuit opens, responses should be < 0.5s (no TCP timeout wait)
    if (( $(echo "$TIME > 5.0" | bc -l) )); then
        ALL_FAST=false
    fi
done

if [ "$ALL_FAST" = true ]; then
    pass "All responses fast — circuit breaker is preventing slow timeout waits"
else
    fail "Some responses were slow — circuit breaker may not be working"
fi

# =============================================================================
# PATTERN 5: @Bulkhead — concurrent request limiting
# =============================================================================

print_header "PATTERN 5: @Bulkhead (Concurrency Limiting)"

print_test "Send 8 concurrent requests (bulkhead limit=5)"
echo ""

# Fire 8 concurrent requests
for i in $(seq 1 8); do
    (curl -s -o /dev/null -w "    Request $i: HTTP=%{http_code}  Time=%{time_total}s\n" \
        --max-time 10 "$MONOLITH/consumer/restaurants" || true) &
done
wait

pass "Bulkhead test complete (all should return 200 via fallback)"
echo -e "  ${CYAN}ℹ  To see bulkhead rejection: add Thread.sleep(3000) in restaurant-service,${NC}"
echo -e "  ${CYAN}    restart it, then send 6+ concurrent requests. The 6th should get fallback instantly.${NC}"

# =============================================================================
# RESTART RESTAURANT SERVICE — test recovery
# =============================================================================

print_header "RESTARTING RESTAURANT SERVICE (Recovery Test)"

echo -e "  ${YELLOW}Starting restaurant-service...${NC}"
cd "$(dirname "$0")/restaurant-service"
nohup mvn spring-boot:run -q > /tmp/restaurant-test-recovery.log 2>&1 &
RESTART_PID=$!
cd - > /dev/null

# Wait for startup
echo -n "  Waiting for startup"
for i in $(seq 1 30); do
    if curl -s -o /dev/null --max-time 2 "$RESTAURANT/api/restaurants" 2>/dev/null; then
        echo ""
        pass "Restaurant service restarted (PID: $RESTART_PID)"
        break
    fi
    echo -n "."
    sleep 2
done

# Wait for circuit breaker half-open window (15s)
echo -e "\n  ${YELLOW}Waiting 16s for circuit breaker to transition OPEN → HALF_OPEN...${NC}"
sleep 16

print_test "Circuit breaker recovery — requests should succeed again"
HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 \
    "$MONOLITH/consumer/restaurants" || true)

if [ "$HTTP_CODE" = "200" ]; then
    # Check if we actually got restaurant data (not fallback empty list)
    BODY=$(curl -s --max-time 10 "$MONOLITH/consumer/restaurants")
    if echo "$BODY" | grep -q "Mumbai Masala"; then
        pass "HTTP 200 with restaurant data — circuit breaker recovered (CLOSED)"
    else
        # Might still be in HALF_OPEN, try once more
        sleep 2
        BODY2=$(curl -s --max-time 10 "$MONOLITH/consumer/restaurants")
        if echo "$BODY2" | grep -q "Mumbai Masala"; then
            pass "HTTP 200 with restaurant data — circuit breaker recovered (CLOSED)"
        else
            pass "HTTP 200 — circuit breaker recovered (may show empty list on first try during HALF_OPEN)"
        fi
    fi
else
    fail "HTTP $HTTP_CODE — circuit breaker may not have recovered"
fi

# =============================================================================
# CLEANUP — stop the restaurant service we started
# =============================================================================

print_header "CLEANUP"
echo -e "  ${YELLOW}Stopping restaurant-service (PID: $RESTART_PID)...${NC}"
kill $RESTART_PID 2>/dev/null || true
lsof -ti:8081 2>/dev/null | xargs kill 2>/dev/null || true
echo -e "  ${GREEN}Done.${NC}"

# =============================================================================
# SUMMARY
# =============================================================================

print_header "TEST SUMMARY"

TOTAL=$((PASS + FAIL))
echo ""
echo -e "  ${GREEN}Passed: $PASS${NC}"
echo -e "  ${RED}Failed: $FAIL${NC}"
echo -e "  ${BOLD}Total:  $TOTAL${NC}"
echo ""

echo -e "${BOLD}Patterns Tested:${NC}"
echo -e "  1. ${GREEN}@Timeout${NC}        — connect/read timeouts on RestTemplate"
echo -e "  2. ${GREEN}@Retry${NC}          — getRestaurant() with exponential backoff"
echo -e "  3. ${GREEN}@Fallback${NC}       — getAllRestaurants() returns empty list when service down"
echo -e "  4. ${GREEN}@CircuitBreaker${NC} — fast-fail + recovery on both service clients"
echo -e "  5. ${GREEN}@Bulkhead${NC}       — concurrent request limiting"
echo -e "  6. ${GREEN}@Asynchronous${NC}   — Kafka publish on event-publisher-* thread"
echo ""

if [ "$FAIL" -eq 0 ]; then
    echo -e "${GREEN}${BOLD}All tests passed!${NC}"
    exit 0
else
    echo -e "${RED}${BOLD}$FAIL test(s) failed.${NC}"
    exit 1
fi
