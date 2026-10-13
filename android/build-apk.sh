#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_HOME="${ANDROID_HOME:-$HOME/Android}"
NDK_VERSION="${NDK_VERSION:-28.2.13676358}"
ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$ANDROID_HOME/ndk/$NDK_VERSION}"
RUST_TOOLCHAIN="${RUST_TOOLCHAIN:-1.98.1}"
ABI="aarch64-linux-android"
HOST_TAG="linux-x86_64"
TOOLCHAIN_BIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$HOST_TAG/bin"

[[ -x "$ANDROID_NDK_HOME/ndk-build" ]] || { echo "Android NDK not found at $ANDROID_NDK_HOME" >&2; exit 2; }
[[ -x "$ANDROID_HOME/build-tools/36.0.0/apksigner" ]] || { echo "Android SDK build-tools 36.0.0 not found under $ANDROID_HOME" >&2; exit 2; }

export ANDROID_HOME ANDROID_SDK_ROOT="$ANDROID_HOME" ANDROID_NDK_HOME ANDROID_NDK_ROOT="$ANDROID_NDK_HOME"
export PATH="$TOOLCHAIN_BIN:$ANDROID_HOME/platform-tools:$PATH"
export CC_aarch64_linux_android="$TOOLCHAIN_BIN/aarch64-linux-android28-clang"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$TOOLCHAIN_BIN/aarch64-linux-android28-clang"
export RUSTUP_NO_UPDATE_CHECK=1

cd "$ROOT"
echo "[1/3] Building Muguet Rust library for arm64-v8a with Rust $RUST_TOOLCHAIN"
cargo "+$RUST_TOOLCHAIN" build --release --lib --target "$ABI"
LIB="$ROOT/target/$ABI/release/libmuguet.so"
[[ -f "$LIB" ]] || { echo "Cargo succeeded but $LIB is missing" >&2; exit 3; }
mkdir -p "$ROOT/android/app/src/main/jniLibs/arm64-v8a"
install -m 0644 "$LIB" "$ROOT/android/app/src/main/jniLibs/arm64-v8a/libmuguet.so"
echo "Native library: $(du -h "$LIB" | cut -f1)"

GRADLE_VERSION="${GRADLE_VERSION:-8.11.1}"
GRADLE_HOME="${GRADLE_HOME:-$HOME/.cache/muguet/gradle-$GRADLE_VERSION}"
GRADLE_BIN="$GRADLE_HOME/bin/gradle"
if [[ ! -x "$GRADLE_BIN" ]]; then
  CACHE="$HOME/.cache/muguet"
  mkdir -p "$CACHE"
  ZIP="$CACHE/gradle-$GRADLE_VERSION-bin.zip"
  echo "[2/3] Downloading Gradle $GRADLE_VERSION"
  curl -fL --retry 3 "https://downloads.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$ZIP"
  TMP="$CACHE/gradle-unpack-$GRADLE_VERSION"
  rm -rf "$TMP" && mkdir -p "$TMP"
  unzip -q "$ZIP" -d "$TMP"
  rm -rf "$GRADLE_HOME"
  mkdir -p "$(dirname "$GRADLE_HOME")"
  mv "$TMP/gradle-$GRADLE_VERSION" "$GRADLE_HOME"
  rmdir "$TMP"
else
  echo "[2/3] Using cached Gradle $GRADLE_VERSION"
fi

echo "[3/3] Building Android APK"
cd "$ROOT/android"
# Gradle's incremental APK packaging can retain the previous ZIP payload on some
# host setups. Remove the old final archive so the new package starts cleanly.
rm -f "$ROOT/android/app/build/outputs/apk/debug/app-debug.apk"
"$GRADLE_BIN" --no-daemon --stacktrace assembleDebug
APK="$ROOT/android/app/build/outputs/apk/debug/app-debug.apk"
[[ -f "$APK" ]] || { echo "Gradle reported success but APK missing: $APK" >&2; exit 4; }
OUT="$ROOT/build/Muguet-Android-arm64-debug.apk"
mkdir -p "$ROOT/build"
cp "$APK" "$OUT"
echo "APK ready: $OUT ($(du -h "$OUT" | cut -f1))"
echo "SHA-256: $(sha256sum "$OUT" | awk '{print $1}')"

if [[ "${INSTALL_TO_DEVICE:-0}" == "1" ]]; then
  command -v adb >/dev/null || { echo "adb not found. Install Android platform-tools or set PATH." >&2; exit 5; }
  DEVICES="$(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')"
  [[ -n "$DEVICES" ]] || { echo "No authorized Android device attached. Enable USB debugging or pair wireless debugging, then retry." >&2; exit 6; }
  echo "Installing on $(printf '%s\n' "$DEVICES" | head -1)"
  adb install -r "$OUT"
fi
