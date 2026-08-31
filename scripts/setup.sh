#!/usr/bin/env bash
#
# setup.sh — Satu perintah untuk menyiapkan JDK 17 + Android SDK lalu build APK.
#
# Cara pakai (Linux / macOS):
#   ./scripts/setup.sh
#
# Yang dilakukan:
#   1. Memeriksa JDK 17+ (kalau belum ada, mengunduh Temurin 17 ke ~/.jdks/temurin-17)
#   2. Memeriksa Android SDK, kalau belum ada mengunduh Command-Line Tools lalu
#      menginstal platform-tools, platforms;android-36, dan build-tools;36.0.0
#   3. Membuat file local.properties (sdk.dir=...)
#   4. Menjalankan ./gradlew assembleDebug
#
# Windows: sebaiknya gunakan Android Studio (lihat README.md).

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="${ROOT_DIR}/scripts/.tmp"
JDK_DIR="${JDK_DIR_OVERRIDE:-$HOME/.jdks/temurin-17}"
CLT_VERSION="15859902"
SDK_PLATFORM="platforms;android-36"
SDK_BUILD_TOOLS="build-tools;36.0.0"

log()  { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33mWARN: %s\033[0m\n' "$*"; }
die()  { printf '\033[1;31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

# ---------------------------------------------------------------- JDK ------
find_java() {
  local bin=""
  if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
    bin="${JAVA_HOME}/bin/java"
  elif command -v java >/dev/null 2>&1; then
    bin="$(command -v java)"
  fi
  if [ -n "$bin" ]; then
    local ver
    ver="$("$bin" -version 2>&1 | head -n1 | sed -E 's/.*version "([0-9]+).*/\1/')"
    if [ "${ver:-0}" -ge 17 ]; then
      echo "$bin"
      return 0
    fi
  fi
  return 1
}

install_jdk() {
  local os_arch
  case "$(uname -s)-$(uname -m)" in
    Linux-x86_64)  os_arch="linux/x64" ;;
    Linux-aarch64 | Linux-arm64) os_arch="linux/aarch64" ;;
    Darwin-x86_64) os_arch="mac/x64" ;;
    Darwin-arm64)  os_arch="mac/aarch64" ;;
    *) die "Platform $(uname -s)-$(uname -m) belum didukung oleh script ini. Install JDK 17+ manual (lihat README)." ;;
  esac

  log "JDK 17 belum ditemukan. Mengunduh Temurin 17 (${os_arch})..."
  mkdir -p "$JDK_DIR" "$TMP_DIR"
  local archive="$TMP_DIR/temurin-17.tar.gz"
  curl -fL --retry 3 --connect-timeout 30 \
    -o "$archive" \
    "https://api.adoptium.net/v3/binary/latest/17/ga/${os_arch}/jdk/hotspot/normal/eclipse"
  rm -rf "${JDK_DIR:?}/"*
  tar -xzf "$archive" -C "$JDK_DIR" --strip-components=1
  export JAVA_HOME="$JDK_DIR"
  log "JDK terpasang di: ${JAVA_HOME}"
}

setup_jdk() {
  local java_bin
  if java_bin="$(find_java)"; then
    export JAVA_HOME="$(dirname "$(dirname "$java_bin")")"
    log "Menggunakan JDK: ${JAVA_HOME}"
  else
    install_jdk
  fi
  "$JAVA_HOME/bin/java" -version 2>&1 | head -n1
  export PATH="$JAVA_HOME/bin:$PATH"
}

# ----------------------------------------------------------- Android SDK ---
sdkmanager_for() {
  local sdk="$1"
  if [ -x "$sdk/cmdline-tools/latest/bin/sdkmanager" ]; then
    echo "$sdk/cmdline-tools/latest/bin/sdkmanager"
  else
    echo ""
  fi
}

install_sdk_tools() {
  local sdk="$1"
  local platform_zip sha expected
  case "$(uname -s)-$(uname -m)" in
    Linux-*) platform_zip="commandlinetools-linux-${CLT_VERSION}_latest.zip"
             sha="4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583" ;;
    Darwin-x86_64) platform_zip="commandlinetools-mac_x86_64-${CLT_VERSION}_latest.zip"
             sha="c5a6378ab5cf7e0d5701921405115befff13e9ff7417fb588389338f8bd050f3" ;;
    Darwin-arm64) platform_zip="commandlinetools-mac_arm64-${CLT_VERSION}_latest.zip"
             sha="835b62a26162b229b441d1f6d4680383815a270809eb33522c0d480fa5002c4e" ;;
    *) die "Platform ini belum didukung script. Gunakan Android Studio (lihat README)." ;;
  esac

  command -v unzip >/dev/null 2>&1 || die "Perintah 'unzip' tidak ditemukan. Install dulu (mis. sudo apt install unzip)."

  log "Mengunduh Android Command-Line Tools (${platform_zip})..."
  mkdir -p "$TMP_DIR"
  local zip_path="$TMP_DIR/$platform_zip"
  curl -fL --retry 3 --connect-timeout 30 -o "$zip_path" \
    "https://dl.google.com/android/repository/${platform_zip}"

  log "Memverifikasi checksum..."
  local actual
  actual="$(sha256sum "$zip_path" | awk '{print $1}')"
  if [ "$actual" != "$sha" ]; then
    die "Checksum tidak cocok untuk ${platform_zip} (expected ${sha}, got ${actual})."
  fi

  log "Menginstal Android SDK ke: ${sdk}"
  mkdir -p "$sdk/cmdline-tools"
  rm -rf "$TMP_DIR/cmdline-tools"
  unzip -q "$zip_path" -d "$TMP_DIR"
  rm -rf "$sdk/cmdline-tools/latest"
  mv "$TMP_DIR/cmdline-tools" "$sdk/cmdline-tools/latest"
}

setup_sdk() {
  local sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [ -z "$sdk" ]; then
    case "$(uname -s)" in
      Darwin) sdk="$HOME/Library/Android/sdk" ;;
      *)      sdk="$HOME/Android/Sdk" ;;
    esac
  fi
  export ANDROID_HOME="$sdk"

  local sdkmanager
  sdkmanager="$(sdkmanager_for "$sdk")"
  if [ -z "$sdkmanager" ]; then
    install_sdk_tools "$sdk"
    sdkmanager="$(sdkmanager_for "$sdk")"
  else
    log "Android SDK sudah ada di: ${sdk}"
  fi
  [ -n "$sdkmanager" ] || die "sdkmanager tidak ditemukan di ${sdk}/cmdline-tools/latest/bin."

  log "Menerima lisensi Android SDK..."
  yes | "$sdkmanager" --sdk_root="$sdk" --licenses >/dev/null 2>&1 || true

  log "Menginstal platform-tools, ${SDK_PLATFORM}, ${SDK_BUILD_TOOLS}..."
  "$sdkmanager" --sdk_root="$sdk" "platform-tools" "$SDK_PLATFORM" "$SDK_BUILD_TOOLS"

  log "Menulis local.properties (sdk.dir=${sdk})..."
  printf 'sdk.dir=%s\n' "$sdk" > "$ROOT_DIR/local.properties"

  echo "$sdk"
}

# -------------------------------------------------------- debug keystore ---
ensure_debug_keystore() {
  local ks="$HOME/.android/debug.keystore"
  if [ -f "$ks" ]; then
    return 0
  fi
  log "Membuat debug keystore: ${ks}"
  mkdir -p "$HOME/.android"
  keytool -genkeypair -v -keystore "$ks" \
    -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
}

# ------------------------------------------------------------------ build ---
main() {
  log "Menyiapkan environment build AI Camera"

  [ -f "$ROOT_DIR/gradlew" ] || die "gradlew tidak ditemukan. Pastikan Anda berada di root repo."

  setup_jdk

  setup_sdk

  ensure_debug_keystore

  log "Menjalankan build: ./gradlew :app:assembleDebug"
  (
    cd "$ROOT_DIR"
    export JAVA_HOME ANDROID_HOME
    ./gradlew --no-daemon :app:assembleDebug
  )

  log "Selesai! APK debug ada di:"
  echo "  ${ROOT_DIR}/app/build/outputs/apk/debug/app-debug.apk"
}

main "$@"
