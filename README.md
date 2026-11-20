# Multi Profil FB

Aplikasi Android Kotlin + Jetpack Compose + Material 3 untuk menjalankan paket Facebook resmi `com.facebook.katana` yang sudah terpasang di perangkat melalui engine BlackBox. Tidak memakai WebView dan tidak membundel APK Facebook.

## Yang disediakan

- Satu profil = satu virtual user BlackBox dengan `userId` sendiri.
- Tambah profil: `createUser(userId)` kemudian `installPackageAsUser("com.facebook.katana", userId)`.
- Ubah nama dan simpan `userId`/nama melalui Preferences DataStore.
- Hapus profil dengan `deleteUser(userId)`, yang menghapus data virtual user tersebut.
- Buka profil menggunakan `launchApk("com.facebook.katana", userId)`.
- Buat pinned shortcut melalui `ShortcutManagerCompat.requestPinShortcut`; Activity perantara membuka user yang sesuai.
- Lifecycle Facebook virtual ditangkap oleh `AppLifecycleCallback`. Bila semua proses Facebook untuk satu user berada di background, service memberi grace period 10 detik lalu memanggil `stopPackage(packageName, userId)`.
- Cache `cache`, `code_cache`, dan external cache dibersihkan saat aplikasi utama kembali terlihat dan setelah profil dihentikan. Database, shared preferences, file login, dan direktori data lain tidak disentuh.
- Manifest membatasi package visibility ke `com.facebook.katana` dan menghapus daftar izin demo BlackBox yang berlebihan, lalu menambahkan izin dasar jaringan serta foreground service.

## Engine BlackBox yang dipakai

Workflow GitHub mengambil source dari `chcdg/FBlackBox` ke `vendor/BlackBox`. Repositori `FBlackBox/BlackBox` saat ini tidak lagi memuat source engine di root repositorinya; mirror/fork `chcdg/FBlackBox` masih menyediakan struktur modul `Bcore` dan API yang dipakai proyek ini. API diverifikasi dari source `BlackBoxCore`, `BActivityThread`, `AppLifecycleCallback`, `BEnvironment`, `CreatePackageExecutor`, dan `CopyExecutor`.

Pemanggilan `installPackageAsUser(packageName, userId)` pada fork ini memakai `InstallOption.installBySystem()`. Jalur `CopyExecutor` tidak menyalin base APK ke direktori virtual per-user untuk opsi tersebut; `sourceDir` Facebook dipakai sebagai sumber paket oleh engine. Data aplikasi tetap dikelola pada direktori user BlackBox yang terpisah. Ini memenuhi tujuan untuk tidak membundel APK dan menghindari salinan base APK per profil pada jalur system-install engine ini.

`ALEX5402/NewBlackbox` tidak dipilih dalam template ini karena pada source `BlackBoxCore` yang diperiksa tidak ditemukan facade API yang sama untuk `installPackageAsUser`, `launchApk`, `createUser`, `deleteUser`, dan `stopPackage`. Jangan mengganti fork tanpa menyesuaikan pemanggilan API dan menguji ulang.

## Arsitektur APK

CI menghasilkan dua APK:

- `app-arm64-v8a-release.apk` — pilih bila library Facebook di HP 64-bit (`nativeLibraryDir` biasanya mengandung `arm64`).
- `app-armeabi-v7a-release.apk` — pilih bila Facebook 32-bit (`nativeLibraryDir` biasanya mengandung `arm`).

Kecocokan ABI penting. HP 64-bit tidak otomatis berarti Facebook yang terpasang adalah APK 64-bit. Layar utama menampilkan petunjuk dari `nativeLibraryDir`; bila tidak dapat dideteksi, verifikasi APK Facebook/perangkat sebelum memilih varian.

## Upload ke GitHub dari HP

1. Unduh ZIP proyek ini, ekstrak dengan aplikasi File di Android.
2. Buka GitHub di browser, buat repository baru, misalnya `multi-profil-fb`.
3. Pastikan branch utama bernama `main`.
4. Upload isi folder proyek (termasuk `.github/workflows/build.yml`, `app/`, `settings.gradle.kts`, dan file Gradle) ke root repository melalui **Add file → Upload files**, lalu commit ke `main`. Jangan upload folder `vendor/BlackBox`; workflow mengunduhnya sendiri.
5. Buka tab **Actions**, pilih **Build Multi Profil FB**, lalu tekan **Run workflow**. Push commit ke `main` juga otomatis memicu build.
6. Setelah selesai, unduh APK dari **Actions → run terbaru → Artifacts**, atau dari **Releases**.

Tidak ada `gradle-wrapper.jar` di proyek. Workflow memakai JDK 17, Gradle 7.6.4, Android SDK, NDK 25.2.9519653, dan CMake 3.22.1.

## Signing key

Workflow memakai GitHub Actions Secrets bila tersedia:

- `KEYSTORE_BASE64` — isi file keystore dalam bentuk Base64.
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

Bila secrets belum diatur, workflow membuat keystore sementara menggunakan `keytool` agar APK release dapat ditandatangani. **Keystore sementara ini berbeda pada build berikutnya**, sehingga APK dari build berikutnya tidak dapat dipasang sebagai update di atas APK sebelumnya tanpa uninstall. Untuk pembaruan yang mempertahankan data aplikasi, buat satu keystore permanen, simpan aman, lalu isi empat secrets tersebut di **Settings → Secrets and variables → Actions**. Jangan simpan keystore atau password di repository publik.

Workflow juga menyertakan file lisensi Apache 2.0 dari checkout BlackBox pada artifact/Release, di samping APK.

## Catatan kompatibilitas dan batasan

1. BlackBox adalah engine lama/eksperimental. README upstream menyebut dukungan Android 5–12, menyarankan target SDK 28 atau lebih rendah, membedakan build 32/64-bit, dan menyatakan stabilitas belum diuji secara luas. Android 10 sesuai rentang yang disebut, tetapi tidak ada jaminan Facebook versi terbaru akan berjalan tanpa masalah.
2. Cara install BlackBox yang dipakai menggunakan `ApplicationInfo.sourceDir` paket host. Facebook versi Play Store dapat berupa paket split. Jika versi Facebook pada perangkat tidak dapat dipasang ke virtual user, kemungkinan perlu dukungan split APK pada engine; jangan menyalin APK Facebook ke project atau menganggap `installPackageAsUser` pasti berhasil.
3. Tidak ada batas jumlah profil di UI. Namun jumlah proses virtual yang bisa hidup bersamaan ditentukan slot proxy dan memori BlackBox/perangkat. Ratusan profil tersimpan tidak berarti ratusan proses dapat aktif bersamaan. Uji dahulu dengan sedikit profil.
4. Pemantauan background menggunakan lifecycle callback dan foreground service. Notifikasi permanen muncul ketika pemantau aktif. Android dapat tetap menghentikan service pada kondisi ekstrem (force-stop, reboot, atau pembatasan OEM); timer 10 detik bukan mekanisme sistem yang absolut.
5. Menghapus cache tidak sama dengan menghentikan jejak semua penyimpanan sementara. File sementara yang sengaja disimpan Facebook di folder lain tidak akan ikut dihapus. Penghapusan hanya menyasar `cache`, `code_cache`, dan external cache.
6. Izin host sengaja dibatasi untuk fitur dasar. Fungsi Facebook yang membutuhkan kamera, mikrofon, lokasi, kontak, atau akses media dapat memerlukan deklarasi dan pengelolaan izin tambahan melalui engine.
7. Proyek ini merupakan template source. Build final harus dijalankan oleh GitHub Actions; environment pembuatan ini tidak memiliki Android SDK/Gradle dan tidak dapat mengunduh dependency GitHub untuk menguji hasil APK di perangkat.

## Struktur

```text
settings.gradle.kts
build.gradle.kts
gradle.properties
app/build.gradle.kts
app/proguard-rules.pro
app/src/main/AndroidManifest.xml
app/src/main/java/com/multifb/profiles/
  MultiProfileApplication.kt
  MainActivity.kt
  ProfileShortcutActivity.kt
  data/FbProfile.kt
  data/ProfileStore.kt
  engine/BlackBoxProfileController.kt
  engine/ProfileCacheCleaner.kt
  monitor/FacebookLifecycleCallback.kt
  monitor/ProfileMonitorService.kt
app/src/main/res/...
.github/workflows/build.yml
```
