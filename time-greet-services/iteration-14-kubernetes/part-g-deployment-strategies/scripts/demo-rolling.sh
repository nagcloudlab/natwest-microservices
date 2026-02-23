#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BASE_DIR="$(dirname "$SCRIPT_DIR")"

echo "=== Rolling Update Demo ==="
echo ""

echo "--- Step 1: Deploy v1 ---"
kubectl apply -f "$BASE_DIR/k8s/rolling/"

echo ""
echo "--- Waiting for deployment ---"
kubectl wait --for=condition=available deployment/greeting-service -n demo --timeout=120s
kubectl wait --for=condition=available deployment/time-service -n demo --timeout=120s

echo ""
echo "--- Current state (v1) ---"
kubectl get pods -n demo -l app=greeting-service -o wide

echo ""
echo "--- Calling greeting-service (should be v1) ---"
for i in 1 2 3; do
  curl -s http://localhost:30001/greeting
  echo ""
done

echo ""
echo "--- Step 2: Rolling update to v2 ---"
kubectl set image deployment/greeting-service greeting-service=greeting-service:v2 -n demo

echo ""
echo "--- Watch the rolling update ---"
kubectl rollout status deployment/greeting-service -n demo

echo ""
echo "--- Calling greeting-service (should be v2) ---"
for i in 1 2 3; do
  curl -s http://localhost:30001/greeting
  echo ""
done

echo ""
echo "--- Rollout history ---"
kubectl rollout history deployment/greeting-service -n demo
