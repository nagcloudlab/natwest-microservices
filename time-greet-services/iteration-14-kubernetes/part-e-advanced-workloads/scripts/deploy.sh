#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BASE_DIR="$(dirname "$SCRIPT_DIR")"

echo "=== Deploying base services ==="
kubectl apply -f "$BASE_DIR/k8s/namespace.yaml"
kubectl apply -f "$BASE_DIR/k8s/greeting-deployment.yaml"
kubectl apply -f "$BASE_DIR/k8s/greeting-service.yaml"
kubectl apply -f "$BASE_DIR/k8s/time-deployment.yaml"
kubectl apply -f "$BASE_DIR/k8s/time-service.yaml"

echo ""
echo "=== Waiting for base deployments ==="
kubectl wait --for=condition=available deployment/greeting-service -n demo --timeout=120s
kubectl wait --for=condition=available deployment/time-service -n demo --timeout=120s

echo ""
echo "=== Base services ready ==="
kubectl get all -n demo

echo ""
echo "=== Demo: Init Container ==="
echo "kubectl apply -f k8s/time-deployment-init.yaml"
echo "kubectl get pods -n demo -w  (watch init container complete before main starts)"
echo ""
echo "=== Demo: Job ==="
echo "kubectl apply -f k8s/job-curl-greeting.yaml"
echo "kubectl logs job/curl-greeting -n demo"
echo ""
echo "=== Demo: CronJob ==="
echo "kubectl apply -f k8s/cronjob-health-check.yaml"
echo "kubectl get cronjobs -n demo"
echo "kubectl get jobs -n demo -w  (watch jobs created every minute)"
echo ""
echo "=== Demo: StatefulSet ==="
echo "kubectl apply -f k8s/statefulset-demo.yaml"
echo "kubectl get pods -n demo  (notice stable names: demo-stateful-0, -1, -2)"
echo ""
echo "=== Demo: DaemonSet ==="
echo "kubectl apply -f k8s/daemonset-logger.yaml"
echo "kubectl get pods -n demo -o wide  (one pod per node)"
