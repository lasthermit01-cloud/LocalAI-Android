#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
"$ROOT/tools/prepare_llama_android.sh"
"$ROOT/tools/build_sd_android.sh"
