#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BASE_DIR="$(dirname "$SCRIPT_DIR")"

echo "=== Deploying to Kubernetes ==="

kubectl apply -f "$BASE_DIR/k8s/namespace.yaml"
kubectl apply -f "$BASE_DIR/k8s/serviceaccount.yaml"
kubectl apply -f "$BASE_DIR/k8s/role-readonly.yaml"
kubectl apply -f "$BASE_DIR/k8s/rolebinding-readonly.yaml"
kubectl apply -f "$BASE_DIR/k8s/greeting-deployment.yaml"
kubectl apply -f "$BASE_DIR/k8s/greeting-service.yaml"
kubectl apply -f "$BASE_DIR/k8s/time-deployment.yaml"
kubectl apply -f "$BASE_DIR/k8s/time-service.yaml"

echo ""
echo "=== Waiting for deployments ==="
kubectl wait --for=condition=available deployment/greeting-service -n demo --timeout=120s
kubectl wait --for=condition=available deployment/time-service -n demo --timeout=120s

echo ""
echo "=== Services running ==="
kubectl get all -n demo

echo ""
echo "=== Demo: RBAC ==="
echo "# demo-reader can list pods:"
echo "kubectl auth can-i list pods -n demo --as=system:serviceaccount:demo:demo-reader"
echo "# demo-reader cannot delete pods:"
echo "kubectl auth can-i delete pods -n demo --as=system:serviceaccount:demo:demo-reader"
echo ""
echo "=== Demo: NetworkPolicy ==="
echo "1. Test without policy: curl http://localhost:30002/time/with-greeting (should work)"
echo "2. Apply deny-all: kubectl apply -f k8s/network-policy-deny-all.yaml"
echo "3. Test again: curl http://localhost:30002/time/with-greeting (should FAIL — timeout)"
echo "4. Apply allow policies:"
echo "   kubectl apply -f k8s/network-policy-allow-dns.yaml"
echo "   kubectl apply -f k8s/network-policy-allow-time-to-greeting.yaml"
echo "   kubectl apply -f k8s/network-policy-allow-external-to-time.yaml"
echo "   kubectl apply -f k8s/network-policy-allow-time-egress.yaml"
echo "5. Test again: curl http://localhost:30002/time/with-greeting (should work)"
echo ""
echo "=== Demo: SecurityContext ==="
echo "kubectl exec deploy/greeting-service -n demo -- id"
echo "(should show uid=1000, non-root)"
