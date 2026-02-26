#!/usr/bin/env bash
#
# compare.sh — Build and compare JVM vs GraalVM Native Image
#
# Usage: ./compare.sh
#
# Prerequisites:
#   - Docker Desktop with 6-8 GB RAM allocated (for native image build)
#   - Ports 9001 and 9002 available
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

COMPOSE_FILE="docker-compose.yml"
JVM_PORT=9001
NATIVE_PORT=9002

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

header() {
    echo ""
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BOLD}  $1${NC}"
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

# ── Step 1: Build both images ──────────────────────────────────────────────────
header "Step 1: Building JVM image"
JVM_BUILD_START=$(date +%s)
docker build -f greeting-service/Dockerfile.jvm -t greeting-jvm greeting-service/
JVM_BUILD_END=$(date +%s)
JVM_BUILD_TIME=$((JVM_BUILD_END - JVM_BUILD_START))
echo -e "${GREEN}JVM image built in ${JVM_BUILD_TIME}s${NC}"

header "Step 2: Building Native image (this takes 3-7 minutes)"
NATIVE_BUILD_START=$(date +%s)
docker build -f greeting-service/Dockerfile.native -t greeting-native greeting-service/
NATIVE_BUILD_END=$(date +%s)
NATIVE_BUILD_TIME=$((NATIVE_BUILD_END - NATIVE_BUILD_START))
echo -e "${GREEN}Native image built in ${NATIVE_BUILD_TIME}s${NC}"

# ── Step 2: Get image sizes ───────────────────────────────────────────────────
JVM_SIZE=$(docker image inspect greeting-jvm --format='{{.Size}}')
NATIVE_SIZE=$(docker image inspect greeting-native --format='{{.Size}}')
JVM_SIZE_MB=$((JVM_SIZE / 1024 / 1024))
NATIVE_SIZE_MB=$((NATIVE_SIZE / 1024 / 1024))

# ── Step 3: Start containers ──────────────────────────────────────────────────
header "Step 3: Starting both containers"
docker compose -f "$COMPOSE_FILE" down 2>/dev/null || true
docker compose -f "$COMPOSE_FILE" up -d

# ── Step 4: Wait for health ───────────────────────────────────────────────────
echo ""
echo -e "${YELLOW}Waiting for services to be healthy...${NC}"

wait_for_health() {
    local port=$1
    local name=$2
    local max_attempts=30
    for i in $(seq 1 $max_attempts); do
        if curl -sf "http://localhost:${port}/actuator/health" > /dev/null 2>&1; then
            echo -e "  ${GREEN}${name} is healthy${NC}"
            return 0
        fi
        sleep 2
    done
    echo "  ERROR: ${name} did not become healthy in time"
    return 1
}

wait_for_health $JVM_PORT "JVM"
wait_for_health $NATIVE_PORT "Native"

# ── Step 5: Collect runtime info ──────────────────────────────────────────────
header "Step 4: Collecting runtime info"

JVM_INFO=$(curl -s "http://localhost:${JVM_PORT}/info/runtime")
NATIVE_INFO=$(curl -s "http://localhost:${NATIVE_PORT}/info/runtime")

echo ""
echo -e "${BOLD}JVM /info/runtime:${NC}"
echo "$JVM_INFO" | python3 -m json.tool 2>/dev/null || echo "$JVM_INFO"

echo ""
echo -e "${BOLD}Native /info/runtime:${NC}"
echo "$NATIVE_INFO" | python3 -m json.tool 2>/dev/null || echo "$NATIVE_INFO"

# Parse values
JVM_STARTUP=$(echo "$JVM_INFO" | python3 -c "import sys,json; print(json.load(sys.stdin)['startupTimeMs'])" 2>/dev/null || echo "N/A")
NATIVE_STARTUP=$(echo "$NATIVE_INFO" | python3 -c "import sys,json; print(json.load(sys.stdin)['startupTimeMs'])" 2>/dev/null || echo "N/A")
JVM_MODE=$(echo "$JVM_INFO" | python3 -c "import sys,json; print(json.load(sys.stdin)['mode'])" 2>/dev/null || echo "N/A")
NATIVE_MODE=$(echo "$NATIVE_INFO" | python3 -c "import sys,json; print(json.load(sys.stdin)['mode'])" 2>/dev/null || echo "N/A")
JVM_MEM=$(echo "$JVM_INFO" | python3 -c "import sys,json; print(json.load(sys.stdin)['memoryUsedMB'])" 2>/dev/null || echo "N/A")
NATIVE_MEM=$(echo "$NATIVE_INFO" | python3 -c "import sys,json; print(json.load(sys.stdin)['memoryUsedMB'])" 2>/dev/null || echo "N/A")

# Get container memory from docker stats
JVM_CONTAINER_MEM=$(docker stats --no-stream --format "{{.MemUsage}}" "$(docker compose -f "$COMPOSE_FILE" ps -q greeting-jvm)" 2>/dev/null | awk '{print $1}')
NATIVE_CONTAINER_MEM=$(docker stats --no-stream --format "{{.MemUsage}}" "$(docker compose -f "$COMPOSE_FILE" ps -q greeting-native)" 2>/dev/null | awk '{print $1}')

# ── Step 6: Print comparison table ────────────────────────────────────────────
header "Comparison: JVM vs GraalVM Native Image"

printf "\n"
printf "  ${BOLD}%-25s  %-15s  %-15s${NC}\n" "Metric" "JVM" "Native"
printf "  %-25s  %-15s  %-15s\n"   "-------------------------" "---------------" "---------------"
printf "  %-25s  %-15s  %-15s\n"   "Mode"                      "$JVM_MODE"       "$NATIVE_MODE"
printf "  %-25s  %-15s  %-15s\n"   "Startup time"              "${JVM_STARTUP} ms" "${NATIVE_STARTUP} ms"
printf "  %-25s  %-15s  %-15s\n"   "Docker image size"         "${JVM_SIZE_MB} MB"  "${NATIVE_SIZE_MB} MB"
printf "  %-25s  %-15s  %-15s\n"   "Heap memory used"          "${JVM_MEM} MB"      "${NATIVE_MEM} MB"
printf "  %-25s  %-15s  %-15s\n"   "Container memory (RSS)"    "$JVM_CONTAINER_MEM" "$NATIVE_CONTAINER_MEM"
printf "  %-25s  %-15s  %-15s\n"   "Build time"                "${JVM_BUILD_TIME}s"  "${NATIVE_BUILD_TIME}s"
printf "\n"

echo -e "${GREEN}Both services running:${NC}"
echo "  JVM:    http://localhost:${JVM_PORT}/greeting"
echo "  Native: http://localhost:${NATIVE_PORT}/greeting"
echo "  JVM:    http://localhost:${JVM_PORT}/info/runtime"
echo "  Native: http://localhost:${NATIVE_PORT}/info/runtime"
echo ""
echo -e "${YELLOW}To stop: docker compose down${NC}"
