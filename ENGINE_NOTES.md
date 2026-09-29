# Native engine notes

The source package intentionally does not contain third-party prebuilt native binaries or model weights.

Pinned llama.cpp Android archive:
- tag: b11146 (llama.cpp v0.5.0 release line)
- archive: llama-b11146-bin-android-arm64.tar.gz
- SHA-256: b0d154dffd3b012cac34830349725a0ee3fb6eb5e11c8aac18527b3357a79687

`tools/prepare_llama_android.sh` fetches and verifies that archive during build.

`tools/build_sd_android.sh` builds stable-diffusion.cpp from its current master with the Android NDK. It attempts Vulkan first for the Mali GPU and retries CPU-only if the Vulkan cross-build fails.

Pinned stable-diffusion.cpp source:
- commit: 3f8527a46c54ecf4cb4ed6003da8e8982283c73c (2026-09-27)
