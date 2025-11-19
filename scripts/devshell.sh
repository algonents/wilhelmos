#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   ./scripts/devshell.sh
#   ./scripts/devshell.sh "runqemu qemux86-64 nographic"

KAS_FILE="kas/qemu-kirkstone.yaml"

if [[ $# -eq 0 ]]; then
  kas shell "${KAS_FILE}"
else
  kas shell "${KAS_FILE}" -c "$*"
fi