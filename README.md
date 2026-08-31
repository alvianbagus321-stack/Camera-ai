# AI Enhance Camera — Camera AI

Aplikasi Android (Jetpack Compose) kamera dengan peningkatan foto AI, fitur on-device
dan AI Horde, plus dukungan Gemini AI (opsional, via API key).

---

## Status kelengkapan build tooling

| Komponen | Status | Keterangan |
| --- | --- | --- |
| File konfigurasi Gradle (`build.gradle.kts`, `settings.gradle.kts`, version catalog) | ✔️ Sudah ada | AGP 9.1.1, Kotlin 2.2.10, KSP |
| **Gradle Wrapper** (`gradlew`, `gradle-wrapper.jar`, `gradle-wrapper.properties`) | ✅ **Ditambahkan** | Gradle 9.3.1 (min. versi yang dibutuhkan AGP 9.1.1) |
| **JDK** | ✅ **Script otomatis + CI** | JDK 17 (Temurin) — diunduh otomatis oleh `scripts/setup.sh` / GitHub Actions |
| **Android SDK** | ✅ **Script otomatis + CI** | `platforms;android-36`, `build-tools;36.0.0`, `platform-tools` |
| `local.properties` | ✅ **Dibuat otomatis** | Diisi `sdk.dir` oleh `scripts/setup.sh` (tidak di-commit, sesuai standar) |
| Signing (debug/release) | ✅ **Diperbaiki** | Debug: keystore otomatis dari AGP. Release: pakai env `KEYSTORE_PATH` atau fallback debug keystore |
| Build CI otomatis | ✅ **Ditambahkan** | `.github/workflows/android-build.yml` |

> Catatan: JDK dan Android SDK tidak dimasukkan ke dalam repo (ukurannya ratusan MB
> dan bergantung platform). Standar yang benar adalah menyediakan *wrapper* + script
> instalasi otomatis, supaya siapa pun bisa build dengan satu perintah.

---

## Cara build

### Opsi 1 — Otomatis (Linux / macOS)

Jalankan satu perintah dari root repo:

```bash
./scripts/setup.sh
```

Script akan:
1. Mengunduh JDK 17 (Temurin) ke `~/.jdks/temurin-17` bila belum ada.
2. Mengunduh Android Command-Line Tools lalu menginstal
   `platform-tools`, `platforms;android-36`, `build-tools;36.0.0`.
3. Membuat `local.properties` (berisi `sdk.dir`).
4. Membuat `~/.android/debug.keystore` bila belum ada (dipakai untuk menandatangani APK).
5. Menjalankan `./gradlew assembleDebug`.

Hasil: `app/build/outputs/apk/debug/app-debug.apk` — langsung bisa di-install.

### Opsi 2 — Android Studio (Windows/Linux/macOS)

1. Install [Android Studio](https://developer.android.com/studio) (sudah termasuk JDK 17+).
2. Buka folder repo ini (`File > Open`).
3. Tunggu sinkronisasi; Android Studio akan menawarkan instalasi *Android SDK Platform 36*
   bila belum ada (klik *Install*).
4. Menu **Build > Build App Bundle(s)/APK(s) > Build APK(s)**.
   Hasil: `app/build/outputs/apk/debug/app-debug.apk`.

### Opsi 3 — GitHub Actions (tanpa perlu setup lokal)

File `.github/workflows/android-build.yml` sudah ada. Setelah di-push ke GitHub,
buka tab **Actions**, pilih workflow **Build APK**, dan unduh artifact
`ai-camera-apk` (berisi APK debug + release).

---

## Perintah Gradle

```bash
./gradlew assembleDebug      # APK debug (instalable, tidak butuh konfigurasi)
./gradlew assembleRelease    # APK release (pakai keystore/fallback debug)
./gradlew installDebug       # Install ke perangkat/emulator yang terhubung
./gradlew clean              # Bersihkan hasil build
```

### Signing release (untuk Play Store / distribusi)

APK release memakai *debug keystore* secara otomatis bila tidak ada konfigurasi,
supaya selalu bisa di-build. Untuk upload key sendiri, set variabel env berikut lalu
buat ulang `assembleRelease`:

```bash
export KEYSTORE_PATH="$PWD/my-upload-key.jks"   # lokasi keystore .jks
export STORE_PASSWORD="password-keystore"
export KEY_ALIAS="upload"
export KEY_PASSWORD="password-key"
./gradlew assembleRelease
```

Buat keystore baru (sekali saja):

```bash
keytool -genkeypair -v -keystore my-upload-key.jks -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
```

Jangan commit file `.jks` — sudah di-`gitignore` (`*.jks`).

---

## Konfigurasi API (opsional)

Salin `.env.example` menjadi `.env` lalu isi `GEMINI_API_KEY` agar fitur Gemini aktif.
Tanpa API key, aplikasi tetap berjalan dengan peningkatan foto on-device / AI Horde.

---

## Troubleshooting

| Masalah | Solusi |
| --- | --- |
| `SDK location not found` | Jalankan `./scripts/setup.sh` (membuat `local.properties`) atau set `ANDROID_HOME`. |
| `Minimum supported Gradle version is 9.3.1` | Pastikan pakai wrapper: `./gradlew` (bukan `gradle` global). |
| `Unsupported class file major version` / butuh Java 17 | Set `JAVA_HOME` ke JDK 17+, atau jalankan `./scripts/setup.sh`. |
| `platforms;android-36` tidak ada | Buka SDK Manager → install Android SDK Platform 36 + Build-Tools 36.0.0. |
| `Keystore file ... debug.keystore ... does not exist` | Jalankan `./scripts/setup.sh` (membuat `~/.android/debug.keystore`), atau buka Android Studio sekali. |
| Download lambat / gagal dari Google | Jalankan ulang script; pastikan koneksi internet normal (dl.google.com & services.gradle.org). |

Versi kunci: AGP `9.1.1` · Gradle wrapper `9.3.1` · Kotlin `2.2.10` · JDK `17+` · compileSdk/targetSdk `36` · minSdk `24`.
