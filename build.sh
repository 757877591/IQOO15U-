#!/usr/bin/env bash
# Local build helper, not a Gradle wrapper. Does not accept SDK licenses automatically.
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$SDK" || ! -f "$SDK/platforms/android-35/android.jar" || ! -d "$SDK/build-tools/35.0.0" ]]; then
  echo 'Install Android SDK Platform 35 and Build-Tools 35.0.0, then set ANDROID_HOME.' >&2
  exit 2
fi
command -v java >/dev/null || { echo 'JDK 17 or 21 is required.' >&2; exit 2; }
command -v javac >/dev/null || { echo 'A complete JDK is required.' >&2; exit 2; }
VERSION=8.11.1
SHA=f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6
CACHE="$ROOT/.build-tools"
ZIP="$CACHE/gradle-$VERSION-bin.zip"
mkdir -p "$CACHE"
if [[ ! -x "$CACHE/gradle-$VERSION/bin/gradle" ]]; then
  command -v curl >/dev/null; command -v unzip >/dev/null
  if [[ ! -f "$ZIP" ]]; then
    curl --fail --location --retry 2 "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip" -o "$ZIP.part"
    mv "$ZIP.part" "$ZIP"
  fi
  if command -v sha256sum >/dev/null; then ACTUAL="$(sha256sum "$ZIP" | cut -d' ' -f1)";
  else ACTUAL="$(shasum -a 256 "$ZIP" | cut -d' ' -f1)"; fi
  [[ "$ACTUAL" == "$SHA" ]] || { echo 'Gradle checksum mismatch. Delete the cached ZIP and retry.' >&2; exit 3; }
  unzip -q -o "$ZIP" -d "$CACHE"
fi
export ANDROID_HOME="$SDK"
cd "$ROOT"
if [[ $# -eq 0 ]]; then set -- :app:assembleDebug :app:lintDebug; fi
"$CACHE/gradle-$VERSION/bin/gradle" --no-daemon --stacktrace "$@"
echo 'Requested Gradle tasks completed. APK variants are under app/build/outputs/apk/.'
