#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BASE_DIR="$(dirname "$SCRIPT_DIR")"

echo "=== Deploying to Kubernetes ==="

kubectl apply -f "$BASE_DIR/k8s/namespace.yaml"
kubectl apply -f "$BASE_DIR/k8s/"

echo ""
echo "=== Waiting for deployments to be ready ==="
kubectl wait --for=condition=available deployment/greeting-service -n demo --timeout=120s
kubectl wait --for=condition=available deployment/time-service -n demo --timeout=120s

echo ""
echo "=== Deployment complete ==="
kubectl get all -n demo

echo ""
echo "=== Access the services ==="
echo "Time Service (NodePort): http://localhost:30002/time"
echo "Time + Greeting:         http://localhost:30002/time/with-greeting"
echo ""
echo "Or use port-forward:"
echo "  kubectl port-forward svc/greeting-service 9001:9001 -n demo"
echo "  kubectl port-forward svc/time-service 9002:9002 -n demo"
echo ""
echo "=== Try scaling greeting-service ==="
echo "  kubectl scale deployment greeting-service --replicas=3 -n demo"
echo "  # Then call /time/with-greeting multiple times to see different hostnames"
