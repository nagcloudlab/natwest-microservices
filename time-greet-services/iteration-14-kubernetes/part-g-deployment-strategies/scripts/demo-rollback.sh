#!/bin/bash
set -euo pipefail

echo "=== Rollback Demo ==="
echo ""

echo "--- Current version ---"
curl -s http://localhost:30001/greeting
echo ""

echo ""
echo "--- Rolling back to previous version ---"
kubectl rollout undo deployment/greeting-service -n demo

echo ""
echo "--- Watching rollback ---"
kubectl rollout status deployment/greeting-service -n demo

echo ""
echo "--- After rollback ---"
curl -s http://localhost:30001/greeting
echo ""
