#!/usr/bin/env bash
# Mode pengembangan Pochi Desktop (jalankan langsung pakai python sistem).
set -euo pipefail
cd "$(dirname "$0")/../desktop"
DISPLAY="${DISPLAY:-:0}" exec /usr/bin/python3 main.py
