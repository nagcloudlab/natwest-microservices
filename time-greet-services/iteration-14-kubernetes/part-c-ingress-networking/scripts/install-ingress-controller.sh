#!/bin/bash
set -euo pipefail

echo "=== Installing NGINX Ingress Controller ==="

kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml

echo ""
echo "=== Waiting for Ingress Controller to be ready ==="
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=120s

echo ""
echo "=== NGINX Ingress Controller is ready ==="
