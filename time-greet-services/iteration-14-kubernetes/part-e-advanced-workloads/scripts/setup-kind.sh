#!/bin/bash
set -euo pipefail

echo "=== Creating KIND cluster ==="

cat <<'EOF' | kind create cluster --name k8s-demo --config=-
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
  - role: control-plane
    extraPortMappings:
      - containerPort: 30002
        hostPort: 30002
        protocol: TCP
  - role: worker
  - role: worker
  - role: worker
EOF

echo ""
echo "=== Cluster created (1 control-plane + 3 workers) ==="
kubectl cluster-info --context kind-k8s-demo
kubectl get nodes
