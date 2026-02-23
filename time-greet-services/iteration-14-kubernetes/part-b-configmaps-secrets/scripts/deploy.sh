#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BASE_DIR="$(dirname "$SCRIPT_DIR")"

echo "=== Deploying to Kubernetes ==="

kubectl apply -f "$BASE_DIR/k8s/namespace.yaml"
kubectl apply -f "$BASE_DIR/k8s/configmap-env.yaml"
kubectl apply -f "$BASE_DIR/k8s/configmap-file.yaml"
kubectl apply -f "$BASE_DIR/k8s/secret.yaml"
kubectl apply -f "$BASE_DIR/k8s/greeting-deployment.yaml"
kubectl apply -f "$BASE_DIR/k8s/greeting-service.yaml"
kubectl apply -f "$BASE_DIR/k8s/time-deployment.yaml"
kubectl apply -f "$BASE_DIR/k8s/time-service.yaml"

echo ""
echo "=== Waiting for deployments ==="
kubectl wait --for=condition=available deployment/greeting-service -n demo --timeout=120s
kubectl wait --for=condition=available deployment/time-service -n demo --timeout=120s

echo ""
echo "=== Deployment complete ==="
kubectl get all -n demo

echo ""
echo "=== Test ==="
echo "curl http://localhost:30002/time"
echo "curl http://localhost:30002/time/with-greeting"
echo ""
echo "=== ConfigMap demo ==="
echo "1. Check current greeting: curl http://localhost:30002/time/with-greeting"
echo "2. Update ConfigMap:"
echo '   kubectl edit configmap greeting-config -n demo'
echo "3. Restart to pick up changes:"
echo '   kubectl rollout restart deployment/greeting-service -n demo'
echo "4. Check again: curl http://localhost:30002/time/with-greeting"
echo ""
echo "=== Secret demo ==="
echo "API key is injected as env var (check apiKeyLoaded in /time response)"
