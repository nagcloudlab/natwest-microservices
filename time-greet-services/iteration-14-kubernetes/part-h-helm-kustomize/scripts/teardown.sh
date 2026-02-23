#!/bin/bash
set -euo pipefail

echo "=== Cleaning up ==="

# Uninstall Helm releases if they exist
helm uninstall demo-dev 2>/dev/null && echo "Uninstalled demo-dev" || true
helm uninstall demo-prod 2>/dev/null && echo "Uninstalled demo-prod" || true

# Delete Kustomize resources if they exist
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BASE_DIR="$(dirname "$SCRIPT_DIR")"
kubectl delete -k "$BASE_DIR/kustomize/overlays/dev" 2>/dev/null || true
kubectl delete -k "$BASE_DIR/kustomize/overlays/prod" 2>/dev/null || true

echo ""
echo "=== Deleting KIND cluster ==="
kind delete cluster --name k8s-demo
echo "=== Done ==="
