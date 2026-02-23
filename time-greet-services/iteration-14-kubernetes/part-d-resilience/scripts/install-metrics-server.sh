#!/bin/bash
set -euo pipefail

echo "=== Installing Metrics Server for KIND ==="

kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml

# Patch metrics-server for KIND (insecure TLS)
kubectl patch deployment metrics-server -n kube-system \
  --type='json' \
  -p='[{"op": "add", "path": "/spec/template/spec/containers/0/args/-", "value": "--kubelet-insecure-tls"}]'

echo ""
echo "=== Waiting for metrics-server ==="
kubectl wait --for=condition=available deployment/metrics-server -n kube-system --timeout=120s

echo ""
echo "=== Metrics Server installed ==="
echo "Wait ~60 seconds, then try: kubectl top nodes / kubectl top pods -n demo"
