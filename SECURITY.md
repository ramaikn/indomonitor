# Pelaporan keamanan

Jangan membuka issue publik berisi token, password, data privat, atau contoh eksploitasi yang membocorkan informasi sensitif.

Gunakan **Report a vulnerability** melalui tab Security GitHub jika fitur private reporting tersedia. Jika belum tersedia, hubungi pemelihara melalui kanal privat yang dicantumkan pada profil GitHub, tanpa mengirim secret terlebih dahulu. Belum ada jaminan waktu respons atau program bug bounty.

Sertakan versi/commit, komponen terdampak, langkah reproduksi dengan data sintetis, dampak, dan saran mitigasi bila ada. Jangan menguji sistem atau akun pihak lain tanpa izin.

## Batas penggunaan

Indomonitor saat ini aplikasi lokal. Publikasi source code tidak menyertakan jaminan deployment aman. Tidak ada login multi-tenant atau pembatasan penggunaan AI per pengguna. Tetap bind ke loopback sampai autentikasi, TLS, retensi, pengelolaan secret, backup, serta batas biaya ditambahkan dan diuji.

Kunci tidak boleh dimasukkan ke source code, screenshot, log, atau issue. Gateway AI lokal dapat meneruskan pertanyaan dan metadata ke penyedia luar. Baca [dokumen keamanan](docs/security.md) sebelum mengoperasikan aplikasi.
