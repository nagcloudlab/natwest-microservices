#!/bin/bash
set -euo pipefail

echo "=== Creating KIND cluster ==="

# KIND cluster config with port mappings
cat <<'EOF' | kind create cluster --name k8s-demo --config=-
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
  - role: control-plane
    extraPortMappings:
      - containerPort: 30001
        hostPort: 30001
        protocol: TCP
      - containerPort: 30002
        hostPort: 30002
        protocol: TCP
  - role: worker
  - role: worker
  - role: worker
EOF

echo ""
echo "=== Cluster created successfully ==="
kubectl cluster-info --context kind-k8s-demo
echo ""
kubectl get nodes
