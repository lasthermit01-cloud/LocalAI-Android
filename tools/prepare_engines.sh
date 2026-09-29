#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
bash "$ROOT/tools/prepare_llama_android.sh"
bash "$ROOT/tools/build_sd_android.sh"
