#!/bin/bash
set -euo pipefail

echo "=== Creating KIND cluster with Calico CNI ==="

cat <<'EOF' | kind create cluster --name k8s-demo --config=-
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
networking:
  disableDefaultCNI: true
  podSubnet: 192.168.0.0/16
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
echo "=== Installing Calico CNI ==="
kubectl apply -f https://raw.githubusercontent.com/projectcalico/calico/v3.27.0/manifests/calico.yaml

echo ""
echo "=== Waiting for Calico to be ready ==="
kubectl wait --for=condition=ready pod -l k8s-app=calico-node -n kube-system --timeout=120s

echo ""
echo "=== Cluster created with Calico CNI ==="
kubectl get nodes
