#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "=== Deployment Strategies Demo ==="
echo ""
echo "Choose a strategy:"
echo "  1) Rolling Update:  ./scripts/demo-rolling.sh"
echo "  2) Rollback:        ./scripts/demo-rollback.sh"
echo "  3) Blue-Green:      ./scripts/demo-blue-green.sh"
echo "  4) Canary:          ./scripts/demo-canary.sh"
echo ""
echo "Prerequisites:"
echo "  ./scripts/setup-kind.sh"
echo "  ./scripts/build-images.sh"
