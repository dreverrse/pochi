#!/usr/bin/env bash
# Mode pengembangan desktop Linux (tauri dev, hot-reload).
set -euo pipefail
cd "$(dirname "$0")/../desktop"
export PATH="$HOME/.cargo/bin:$PATH"
npm run tauri dev
