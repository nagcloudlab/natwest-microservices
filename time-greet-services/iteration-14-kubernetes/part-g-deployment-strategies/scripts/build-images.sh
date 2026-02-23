#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BASE_DIR="$(dirname "$SCRIPT_DIR")"

echo "=== Building greeting-service v1 ==="
docker build -t greeting-service:v1 "$BASE_DIR/greeting-service-v1"

echo ""
echo "=== Building greeting-service v2 ==="
docker build -t greeting-service:v2 "$BASE_DIR/greeting-service-v2"

echo ""
echo "=== Building time-service ==="
docker build -t time-service:1.0.0 "$BASE_DIR/time-service"

echo ""
echo "=== Loading images into KIND ==="
kind load docker-image greeting-service:v1 --name k8s-demo
kind load docker-image greeting-service:v2 --name k8s-demo
kind load docker-image time-service:1.0.0 --name k8s-demo

echo "=== Done ==="
