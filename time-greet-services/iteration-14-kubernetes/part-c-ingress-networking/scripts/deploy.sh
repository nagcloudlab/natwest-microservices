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

echo ""
echo "=== Waiting for deployments ==="
kubectl wait --for=condition=available deployment/greeting-service -n demo --timeout=120s
kubectl wait --for=condition=available deployment/time-service -n demo --timeout=120s

echo ""
echo "=== Applying path-based Ingress ==="
kubectl apply -f "$BASE_DIR/k8s/ingress-path-based.yaml"

echo ""
echo "=== Deployment complete ==="
kubectl get all -n demo
kubectl get ingress -n demo

echo ""
echo "=== Path-based routing (default) ==="
echo "curl http://localhost/greeting"
echo "curl http://localhost/time"
echo "curl http://localhost/time/with-greeting"
echo ""
echo "=== Host-based routing (apply separately) ==="
echo "kubectl apply -f k8s/ingress-host-based.yaml"
echo "curl -H 'Host: greeting.local' http://localhost/greeting"
echo "curl -H 'Host: time.local' http://localhost/time"
echo ""
echo "=== TLS (apply separately) ==="
echo "./scripts/generate-tls-cert.sh"
echo "kubectl apply -f k8s/ingress-tls.yaml"
echo "curl -k https://demo.local/greeting  (add '127.0.0.1 demo.local' to /etc/hosts)"
