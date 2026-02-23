#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BASE_DIR="$(dirname "$SCRIPT_DIR")"

echo "=== Canary Deployment Demo ==="
echo ""

echo "--- Step 1: Deploy stable (v1, 3 replicas) + canary (v2, 1 replica) ---"
kubectl apply -f "$BASE_DIR/k8s/canary/"

echo ""
echo "--- Waiting for deployments ---"
kubectl wait --for=condition=available deployment/greeting-stable -n demo --timeout=120s
kubectl wait --for=condition=available deployment/greeting-canary -n demo --timeout=120s
kubectl wait --for=condition=available deployment/time-service -n demo --timeout=120s

echo ""
echo "--- Traffic split: ~75% v1, ~25% v2 ---"
echo "--- Sending 20 requests ---"
echo ""
V1=0
V2=0
for i in $(seq 1 20); do
  RESPONSE=$(curl -s http://localhost:30001/greeting)
  VERSION=$(echo "$RESPONSE" | grep -o '"version":"v[12]"' | grep -o 'v[12]')
  echo "Request $i: $VERSION"
  if [ "$VERSION" = "v1" ]; then V1=$((V1+1)); fi
  if [ "$VERSION" = "v2" ]; then V2=$((V2+1)); fi
done

echo ""
echo "=== Results: v1=$V1, v2=$V2 ==="
echo ""
echo "--- To promote canary (scale up v2, scale down v1): ---"
echo "kubectl scale deployment greeting-canary --replicas=4 -n demo"
echo "kubectl scale deployment greeting-stable --replicas=0 -n demo"
echo ""
echo "--- To rollback canary: ---"
echo "kubectl delete deployment greeting-canary -n demo"
