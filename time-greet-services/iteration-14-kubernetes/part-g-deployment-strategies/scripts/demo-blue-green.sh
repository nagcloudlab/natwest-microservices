#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BASE_DIR="$(dirname "$SCRIPT_DIR")"

echo "=== Blue-Green Deployment Demo ==="
echo ""

echo "--- Step 1: Deploy blue (v1) and green (v2) ---"
kubectl apply -f "$BASE_DIR/k8s/blue-green/"

echo ""
echo "--- Waiting for deployments ---"
kubectl wait --for=condition=available deployment/greeting-blue -n demo --timeout=120s
kubectl wait --for=condition=available deployment/greeting-green -n demo --timeout=120s
kubectl wait --for=condition=available deployment/time-service -n demo --timeout=120s

echo ""
echo "--- Service points to BLUE (v1) ---"
for i in 1 2 3; do
  curl -s http://localhost:30001/greeting
  echo ""
done

echo ""
read -p "Press Enter to switch to GREEN (v2)..."

echo ""
echo "--- Switching service to GREEN (v2) ---"
kubectl patch service greeting-service -n demo \
  -p '{"spec":{"selector":{"version":"green"}}}'

echo ""
echo "--- Service now points to GREEN (v2) ---"
for i in 1 2 3; do
  curl -s http://localhost:30001/greeting
  echo ""
done

echo ""
echo "--- To rollback to blue: ---"
echo 'kubectl patch service greeting-service -n demo -p '\''{"spec":{"selector":{"version":"blue"}}}'\'''
