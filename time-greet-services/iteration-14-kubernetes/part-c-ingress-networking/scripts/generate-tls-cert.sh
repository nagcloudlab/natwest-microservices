#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "=== Generating self-signed TLS certificate ==="

openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout "$SCRIPT_DIR/tls.key" \
  -out "$SCRIPT_DIR/tls.crt" \
  -subj "/CN=demo.local/O=Demo"

echo ""
echo "=== Creating TLS Secret in Kubernetes ==="
kubectl create secret tls demo-tls-secret \
  --cert="$SCRIPT_DIR/tls.crt" \
  --key="$SCRIPT_DIR/tls.key" \
  -n demo \
  --dry-run=client -o yaml | kubectl apply -f -

rm -f "$SCRIPT_DIR/tls.key" "$SCRIPT_DIR/tls.crt"

echo ""
echo "=== TLS Secret created ==="
