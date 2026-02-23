#!/bin/bash
set -euo pipefail

echo "=== Deleting KIND cluster ==="
kind delete cluster --name k8s-demo
echo "=== Done ==="
