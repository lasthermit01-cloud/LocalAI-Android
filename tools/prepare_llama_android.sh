#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/app/src/main/jniLibs/arm64-v8a"
TMP="$ROOT/.engine-build/llama"
mkdir -p "$OUT" "$TMP"
VERSION="b11146"
ARCHIVE="$TMP/llama.tar.gz"
URL="https://github.com/ggml-org/llama.cpp/releases/download/${VERSION}/llama-${VERSION}-bin-android-arm64.tar.gz"
SHA="b0d154dffd3b012cac34830349725a0ee3fb6eb5e11c8aac18527b3357a79687"
if [[ ! -f "$ARCHIVE" ]]; then curl -fL --retry 4 -o "$ARCHIVE" "$URL"; fi
echo "$SHA  $ARCHIVE" | sha256sum -c -
rm -rf "$TMP/unpack"; mkdir -p "$TMP/unpack"; tar -xzf "$ARCHIVE" -C "$TMP/unpack"
CLI="$(find "$TMP/unpack" -type f -name 'llama-cli' | head -1 || true)"
SERVER="$(find "$TMP/unpack" -type f -name 'llama-server' | head -1 || true)"
[[ -n "$CLI" && -n "$SERVER" ]] || { echo "llama-cli/server not found in archive"; exit 1; }
find "$TMP/unpack" -type f -name '*.so' -exec cp -f {} "$OUT/" \;
cp -f "$CLI" "$OUT/libllama_cli.so"
cp -f "$SERVER" "$OUT/libllama_server.so"
chmod +x "$OUT/libllama_cli.so" "$OUT/libllama_server.so"
echo "llama.cpp Android engine prepared in $OUT"
