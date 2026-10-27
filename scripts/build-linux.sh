#!/usr/bin/env bash
# Satu perintah: build desktop Linux (Tauri) — rilis.
set -euo pipefail
cd "$(dirname "$0")/../desktop"

export PATH="$HOME/.cargo/bin:$PATH"

# 1. deps frontend
npm install --no-audit --no-fund

# 2. build rilis Tauri (juga build frontend)
npm run tauri build
