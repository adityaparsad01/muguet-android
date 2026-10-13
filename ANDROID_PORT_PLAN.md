# Muguet Android Port: Repository Audit and Implementation Plan

Audit date: 2026-10-13
Upstream: https://github.com/c22dev/muguet
Working copy: `/home/aditya/projects/muguet-android-port`

## Executive summary

This repository is not an iOS-only game engine. Most of the game bridge/runtime is Rust, `wgpu` handles the graphics abstraction, and `winit` owns the window/event loop. Those are promising reuse points. The current application and build are still Apple-oriented, however, and an Android port is a real platform port, not just changing the Cargo target.

Recommended first target: `aarch64-linux-android` on a Vulkan-capable phone. Keep the existing `main` branch untouched and develop Android work on a separate branch.

## Findings from source

### Reusable components (subject to Android compilation/testing)
- `src/host.rs`, `src/fs.rs`, `src/io.rs`, `src/mp.rs`, `src/proto.rs`, `src/server.rs`: game host imports, filesystem and I/O bridge, multiplayer/protocol/server logic.
- `src/gpu.rs`: renderer is written against `wgpu`; it should be possible to retain most of the backend-independent renderer, but verify all `wgpu` API/backend assumptions and shader support on Android Vulkan.
- `src/app.rs`: `winit` event loop and game lifecycle can be reused conceptually, but Android lifecycle/entry-point behavior must be implemented and tested.
- `src/import.rs`: ZIP extraction/validation is platform-neutral, but the UI must provide a safe way to select the ZIP and place it in app-private storage.

### Blocking Apple-specific code
1. `Cargo.toml` has unconditional Apple Objective-C dependencies (`objc2`, `objc2-foundation`, `objc2-core-foundation`, `objc2-game-controller`). `src/pad.rs` imports Apple GameController unconditionally. These need target-specific dependencies and an Android-compatible gamepad implementation (or a no-op first pass).
2. `src/keyboard.rs` and `src/touch.rs` are compiled only for iOS today. `touch.rs` uses UIKit/CoreFoundation Objective-C types and cannot be used on Android. Add a separate Android touch/UI implementation using `winit` touch events and/or Android native UI.
3. `src/app.rs` hardcodes `wgpu::Backends::METAL`. Android needs a supported backend, normally Vulkan (`wgpu::Backends::VULKAN`), and must create a surface from Android's native window.
4. `src/config.rs::support_dir()` assumes `~/Library/Application Support/Muguet` on non-iOS. Android must use app-private storage, not this macOS path or a guessed `$HOME`; pass an Android app data directory from the Android entry point/environment and persist config, cache, saves, and imported game data there.
5. `src/main.rs` contains Darwin-only process memory and QoS APIs (`proc_pid_rusage`, `pthread_set_qos_class_self_np`, `QOS_CLASS_*`) and uses Apple-specific precompile logic. Gate these APIs by target and provide portable Android/Linux alternatives or safe no-op implementations.
6. `src/main.rs` and `build.rs` special-case iOS for precompiled game code and Apple linker sections. Android must not reuse the Mach-O `__GAME` section or iOS code-signing approach. Decide whether the first Android build uses Wasmtime JIT or ahead-of-time precompiled code.
7. `build.rs`, `build.sh`, `make-ios.sh`, `ios/project.yml`, and Swift launchers are Apple-specific. Add a separate Android Gradle project and Android-specific build script instead of trying to adapt the iOS shell scripts in place.
8. Audio (`src/audio.rs`, `cpal` dependency) must be checked against the selected Android target/backend and audio permissions/device behavior.
9. `src/io.rs` uses Unix file APIs (`FileExt`); confirm Android's target support and verify file offsets/threading behavior. Do not assume all macOS/Linux libc APIs or constants are identical.
10. `footprint_mb()` currently uses Darwin `proc_pid_rusage` and will need a cfg-gated implementation. `raise_open_file_limit()` also assumes a Darwin `RLIMIT_NOFILE` constant; gate or replace it with target-correct values.

## Proposed Android architecture

- `android/`: Android Gradle project, manifest, Kotlin/Java Activity, and `jniLibs/arm64-v8a` output integration.
- Native Rust executable/library built for `aarch64-linux-android` with the Android NDK and Cargo.
- `winit` Android activity integration and Android lifecycle handling. Confirm the exact supported entry point for the pinned `winit` version before writing glue.
- `wgpu` Vulkan backend; no Metal references in the Android code path.
- App-private `filesDir` for `config.json`, saves, imported `data/` and `b/<build>/shaders/`, and compiled-code cache.
- Android document picker (`ACTION_OPEN_DOCUMENT` / Storage Access Framework) for selecting the user's own game ZIP. Copy to app-private storage, then reuse the Rust ZIP import/validation path.
- Touch controls: port the existing control layout/behavior from `src/touch.rs` to a platform-neutral input layer, with Android touch events and no UIKit/Objective-C dependencies.
- Controller support: initially rely on `winit` keyboard/gamepad events if available; add Android-native controller mapping after the APK can launch and render.
- Multiplayer UDP logic should be reused, with Android network/lifecycle testing and a clear user-facing permission/connection flow.

## Build strategy

1. Preserve the upstream default branch. Use this isolated working branch for the port.
2. Add target-specific Cargo dependencies and cfg-gate Apple APIs first; get a native Android-target `cargo check` before creating UI polish.
3. Determine Wasmtime 49 support and required features for `aarch64-linux-android`. The repository currently enables `runtime`, `threads`, `profiling`, `anyhow`, and optional Cranelift/JIT features. Validate each feature on the actual Android target. If JIT is unavailable or blocked by Android execution policy, evaluate a supported precompiled-code path; do not assume the iOS Mach-O embedding mechanism works on Android.
4. Add the minimal Android Activity + native window/surface + Vulkan renderer smoke test.
5. Add app-private storage and game ZIP import.
6. Port touch controls and Android audio/gamepad behavior.
7. Build an arm64 APK, install on the Nothing Phone 3, and inspect `adb logcat` for native crashes, Vulkan failures, memory exhaustion, or missing files.
8. Only then profile frame time, memory use, shader/texture compatibility, and long-session stability.

## Validation gates

- `cargo fmt --check`
- Android-target `cargo check` / `cargo build` with pinned Rust toolchain and NDK
- APK contains `lib/arm64-v8a/` native library and Android manifest has the required activity configuration
- App starts, creates a Vulkan surface, and displays a renderer smoke test
- User-selected ZIP imports without path traversal or partial/corrupt state
- Game WASM module loads and its imports/threads/shared memory work on device
- Touch, lifecycle pause/resume, audio, save/load, and multiplayer are individually tested
- Record logs and performance metrics; do not call the port complete merely because an APK builds

## Current status

- [x] Cloned upstream repository into a separate working directory.
- [x] Created isolated branch `android-port/research`.
- [x] Audited Cargo dependencies, platform cfgs, renderer initialization, storage paths, launcher, and build scripts.
- [x] Android Rust target library check passes with Rust 1.98.1 and NDK 28.2.13676358.
- [x] Apple-only dependencies are target-gated; Android native activity, Vulkan selection, app-private storage, and initial touch mapping added.
- [x] Android Gradle launcher and debug APK package successfully.
- [x] APK signature and native AArch64 library/entry symbols verified.
- [ ] Vulkan surface smoke test on a real phone.
- [ ] Game ZIP import and actual GTA V launch on a real phone.
- [ ] Touch control tuning, audio, pause/resume, memory and multiplayer validation.

## Important limitation

The current audit establishes a credible porting path, not a working Android build. The biggest unknown is whether the current Wasmtime 49 feature set and the game's WASM requirements can compile and execute on the chosen Android target. That must be tested before committing to the final runtime packaging strategy.

## Host environment check (2026-10-13)

- Home server OS: Ubuntu 24.04; default Rust was `1.94.1`.
- The first `cargo check --target aarch64-linux-android` stopped before compiling because Wasmtime 49 / Cranelift 0.136 require Rust 1.96.0 or newer. Rust toolchain `1.98.1` was installed to match the toolchain family already used by the upstream iOS script.
- The Android Rust standard-library target is being installed for toolchain `1.98.1`.
- The SDK is under `/home/aditya/Android`, with NDK `28.2.13676358`, platform API 36 and build-tools 36.0.0. Gradle 8.11.1 is cached under `~/.cache/muguet/gradle-8.11.1`.
- The Rust target check passed, and `./android/build-apk.sh` completed successfully. The script removes any old debug APK before packaging so repeated builds do not append a previous ZIP payload. The resulting APK is `/home/aditya/projects/muguet-android-port/build/Muguet-Android-arm64-debug.apk` (26 MB), SHA-256 `5f28825006ad6e28e10eac8a8627129d73655760f8da45922d370e13a868745a`.
- APK signature verification passed using APK Signature Scheme v2. `aapt dump badging` confirms package `com.c22dev.muguet.android`, minimum SDK 26, target SDK 35 and native ABI `arm64-v8a`. The embedded ELF is AArch64 and exports both `ANativeActivity_onCreate` and `android_main`.
- `adb devices -l` reported no attached devices, so there has been no device install, renderer/Vulkan smoke test, or GTA V gameplay test. Treat the APK as a built experimental port, not yet a confirmed playable Android release.
