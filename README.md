# FF6 Advance

Aplikasi pembungkus mGBA (libretro) dengan fitur Import Game.

## Build pengujian
1. Upload isi folder proyek ke repositori GitHub.
2. Jalankan workflow **Build APK** dari tab Actions.
3. Unduh artifact `FF6Advance-debug-APK`.
4. APK debug hanya untuk pengujian dan dapat memicu peringatan Play Protect.

## Build release yang ditandatangani
Untuk membuat APK release dengan identitas penandatanganan milik sendiri, buat Android release keystore yang aman dan simpan cadangannya. Jangan unggah keystore ke repositori.

Tambahkan empat GitHub Actions Secrets berikut:
- `RELEASE_KEYSTORE_BASE64`: isi file keystore dalam format Base64.
- `RELEASE_STORE_PASSWORD`: password keystore.
- `RELEASE_KEY_ALIAS`: alias kunci.
- `RELEASE_KEY_PASSWORD`: password kunci.

Setelah secrets tersedia, jalankan workflow lagi dan unduh artifact `FF6Advance-release-APK`. Gunakan kunci yang sama untuk semua pembaruan aplikasi agar Android mengenali pembaruan sebagai aplikasi yang sama.

## Catatan keamanan
Penandatanganan release tidak menjamin Play Protect akan mengizinkan instalasi. Play Protect dapat menilai reputasi APK, library native, dan perilaku aplikasi. Jangan menonaktifkan perlindungan hanya untuk melewati deteksi. Jika APK release tetap diblokir, tinjau detail deteksi dan ajukan peninjauan ke Google.

Core mGBA diambil dari buildbot Libretro saat workflow berjalan. Perubahan pada sumber core dapat memengaruhi hasil pemindaian dan kompatibilitas.

Di aplikasi: Import Game, pilih file `.gba` milikmu, lalu ketuk untuk bermain.
