#!/usr/bin/env bash
# Satu perintah: jalankan Pochi Desktop (PyQt6).
# Stack ringan (dipilih sesuai hasil M0): Python + PyQt6 di /usr/bin/python3.
set -euo pipefail
cd "$(dirname "$0")/../desktop"

# Pastikan dependensi (opsional, sekali saja)
if [ ! -d .venv ]; then
  python3 -m venv .venv
  .venv/bin/pip install -r requirements.txt
fi

DISPLAY="${DISPLAY:-:0}" exec .venv/bin/python3 main.py
