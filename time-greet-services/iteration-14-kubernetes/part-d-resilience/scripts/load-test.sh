#!/bin/bash
set -euo pipefail

echo "=== Load testing greeting-service ==="
echo "Sending requests for 60 seconds..."
echo "Watch HPA in another terminal: kubectl get hpa -n demo -w"
echo ""

# Run load test using kubectl exec into a temp pod
kubectl run load-test --rm -i --tty --restart=Never \
  --image=busybox \
  -n demo \
  -- /bin/sh -c "while true; do wget -q -O- http://greeting-service:9001/greeting > /dev/null 2>&1; done" &

LOAD_PID=$!

sleep 60

echo ""
echo "=== Stopping load test ==="
kubectl delete pod load-test -n demo --ignore-not-found=true 2>/dev/null || true
kill $LOAD_PID 2>/dev/null || true

echo ""
echo "=== Check HPA status ==="
kubectl get hpa -n demo
kubectl get pods -n demo
