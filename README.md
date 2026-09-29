# LocalAI Android

A local-first Android 16 application profile for POCO X6 Pro (arm64, 12 GB physical RAM). It runs text through `llama.cpp` and images through `stable-diffusion.cpp` on the phone.

## Privacy / filtering model

- No cloud inference.
- No analytics, telemetry, account, ad SDK, crash-reporting SDK, moderation API, keyword blacklist, or remote safety classifier.
- `INTERNET` is present for the built-in model downloader and for loopback (`127.0.0.1`) communication with the local `llama-server` process.
- Chats and outputs are stored locally in the app-specific external-files directory.
- Behavior is determined by the model weights and editable system prompt.

## One-button models

Built-in downloader entries (not bundled in the APK):

1. Qwen3-4B-2507-Instruct-Uncensored-HauhauCS-Aggressive Q4_K_M, ~2.5 GB.
2. DreamShaper 8 pruned safetensors, ~2.13 GB.
3. Optional Qwen3-8B-Abliterated Q4_K_M, ~5.03 GB.

Downloads support `.part` resume and are verified with hard-coded SHA-256 before being renamed into the model directory.

## Build

Requirements: JDK 17, Android SDK platform 36/build-tools 36.0.0, NDK 28.2.13676358, CMake, Ninja, Git and curl.

```bash
export ANDROID_NDK_HOME="$ANDROID_SDK_ROOT/ndk/28.2.13676358"
bash tools/prepare_engines.sh
gradle assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

The included GitHub Actions workflow performs the native-engine preparation and APK build automatically. It first tries Vulkan for `stable-diffusion.cpp` and falls back to CPU if Android Vulkan cross-compilation fails.

## Native executable packaging

Android's native library directory is used as an executable location. The build scripts package:

- `llama-server` as `libllama_server.so`
- `llama-cli` as `libllama_cli.so`
- `sd-cli` as `libsd_cli.so`
- dependent `.so` libraries alongside them

`android:extractNativeLibs="true"` and legacy JNI packaging ensure they are extracted on device. `LD_LIBRARY_PATH` is set to the app native-library directory before child processes start.

## Defaults for POCO X6 Pro

- arm64-v8a only
- llama CPU backend by default (`-ngl 0`) for broad MediaTek compatibility
- 6 generation threads / 8 batch threads
- 4096 context by default, optional 8192
- image default 512×512, 20 steps, Euler A
- SD Vulkan build attempted first, CPU fallback available

## CI

Pushes to `main` build a debug APK and publish it as the `LocalAI-debug-apk` GitHub Actions artifact.

## Licenses

This repository does not redistribute model weights. Before distributing a build to others, review the licenses/model cards of the models exposed by the downloader and the licenses of llama.cpp and stable-diffusion.cpp.
