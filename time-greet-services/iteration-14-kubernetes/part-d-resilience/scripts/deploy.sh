#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BASE_DIR="$(dirname "$SCRIPT_DIR")"

echo "=== Deploying to Kubernetes ==="

kubectl apply -f "$BASE_DIR/k8s/namespace.yaml"
kubectl apply -f "$BASE_DIR/k8s/greeting-deployment.yaml"
kubectl apply -f "$BASE_DIR/k8s/greeting-service.yaml"
kubectl apply -f "$BASE_DIR/k8s/time-deployment.yaml"
kubectl apply -f "$BASE_DIR/k8s/time-service.yaml"
kubectl apply -f "$BASE_DIR/k8s/hpa-greeting.yaml"
kubectl apply -f "$BASE_DIR/k8s/pdb-greeting.yaml"

echo ""
echo "=== Waiting for deployments ==="
kubectl wait --for=condition=available deployment/greeting-service -n demo --timeout=120s
kubectl wait --for=condition=available deployment/time-service -n demo --timeout=120s

echo ""
echo "=== Deployment complete ==="
kubectl get all -n demo

echo ""
echo "=== Demo: Liveness probe ==="
echo "1. Port-forward: kubectl port-forward svc/greeting-service 9001:9001 -n demo"
echo "2. Make a pod unhealthy: curl -X POST http://localhost:9001/greeting/fail"
echo "3. Watch pods: kubectl get pods -n demo -w"
echo "   (K8s will restart the unhealthy pod after liveness probe fails)"
echo ""
echo "=== Demo: HPA ==="
echo "1. Install metrics-server: ./scripts/install-metrics-server.sh"
echo "2. Load test: ./scripts/load-test.sh"
echo "3. Watch HPA: kubectl get hpa -n demo -w"
