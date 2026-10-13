# Muguet Android port

This is an experimental arm64 Android port of c22dev/muguet. It is maintained on the local branch `android-port/research`; the upstream `main` branch is not changed.

## Build an APK on Ubuntu

The home-server setup used for this port is:

- Android SDK: `$HOME/Android`
- Android NDK: `28.2.13676358`
- Android platform/build tools: API 36 / build-tools 36.0.0
- Rust toolchain: `1.98.1`
- Gradle: the build script downloads Gradle 8.11.1 if it is not cached

Run:

```bash
cd /home/aditya/projects/muguet-android-port
./android/build-apk.sh
```

Expected output:

```text
build/Muguet-Android-arm64-debug.apk
```

The script cross-compiles the Rust `cdylib` for `aarch64-linux-android`, copies it into the Android app's `jniLibs/arm64-v8a`, and runs Gradle to package a debug APK. Set `ANDROID_HOME`, `ANDROID_NDK_HOME`, or `NDK_VERSION` if the SDK lives somewhere else. On the home server used for this port, the verified output was a 26 MB APK, signed with the debug certificate, with an AArch64 `libmuguet.so` and the NativeActivity entry symbols present. The complete APK build succeeded; final APK SHA-256: `5f28825006ad6e28e10eac8a8627129d73655760f8da45922d370e13a868745a`. Actual gameplay has not been verified because no Android device was attached through ADB during the build.

## First launch and game ZIP

The APK does not include GTA V game data. Start **Muguet Android**, choose the user's own ZIP from the Android document picker, and then tap **Start Muguet**. The launcher checks that the archive contains entries for `data/manifest.json`, `b/<build>/shaders/index.json`, and `b/<build>/game.wasm`. Rust then imports the ZIP into app-private storage. The archive should be the same layout accepted by the upstream Muguet web port.

To install after enabling USB debugging or pairing wireless ADB to the home server:

```bash
adb devices -l
adb install -r /home/aditya/projects/muguet-android-port/build/Muguet-Android-arm64-debug.apk
```

Or request the build script to install automatically after building by setting `INSTALL_TO_DEVICE=1` (see the script's install option).

## Implementation notes

- Android native lifecycle is backed by `winit` 0.30.13 plus `android-activity`'s NativeActivity integration.
- Android renderer selection uses Vulkan; Apple targets retain Metal.
- Apple Objective-C/GameController crates are target-specific; non-Apple builds use a stub gamepad provider for now.
- Android config, saves, cached code, and the game archive live below the app-private `files/Muguet` directory.
- Touch input is a preliminary mapping: lower-left drag for movement, drag elsewhere for looking, lower-right zones for actions, upper-left for pause. It still needs on-device tuning and a visible control overlay.
- Only `arm64-v8a` is configured. No x86 emulator ABI is packaged.

## Validation status

A Rust Android-target `cargo check --lib --target aarch64-linux-android` passed with Rust 1.98.1 and the NDK compiler configured. This confirms Rust target compilation, not a working game session. A release native-library build and APK packaging are being verified separately. An Android device was not attached to the home server during the current test session, so installation, Vulkan driver behavior, gameplay, audio, touch tuning, and memory stability remain unverified until on-device testing.
