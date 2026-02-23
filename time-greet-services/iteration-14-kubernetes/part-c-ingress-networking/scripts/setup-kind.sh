#!/bin/bash
set -euo pipefail

echo "=== Creating KIND cluster with Ingress port mappings ==="

cat <<'EOF' | kind create cluster --name k8s-demo --config=-
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
  - role: control-plane
    kubeadmConfigPatches:
      - |
        kind: InitConfiguration
        nodeRegistration:
          kubeletExtraArgs:
            node-labels: "ingress-ready=true"
    extraPortMappings:
      - containerPort: 80
        hostPort: 80
        protocol: TCP
      - containerPort: 443
        hostPort: 443
        protocol: TCP
  - role: worker
  - role: worker
  - role: worker
EOF

echo ""
echo "=== Cluster created ==="
kubectl cluster-info --context kind-k8s-demo
