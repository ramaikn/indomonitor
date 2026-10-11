# Verifikasi Indomonitor

## Pemeriksaan publikasi, 11 Oktober 2026

- Checkout utama dipindahkan ke `project/indomonitor` dalam workspace lamaran; namespace, judul browser, navigasi, logo, dan tautan publik memakai Indomonitor. Repo tetap mandiri, dengan backend/frontend/pipeline/docs dalam satu monorepo.
- Pengujian final: **6 Python tests lulus**, Ruff lulus, **16 Java tests lulus**, dan build TypeScript/Vite berhasil. Database test terpisah; database aplikasi tidak diisi fixture. Launcher pytest setelah pemindahan folder diperbaiki menjadi `uv run --link-mode hardlink --module pytest`.
- Browser desktop memverifikasi dashboard berisi 267 URL dari 38 domain dalam filter aktif. Data terakhir 8 Oktober 2026, bukan klaim data terbaru. Dashboard hanya menampilkan waktu data terakhir; status partial/gagal dan detail riwayat tetap terlihat di Pengambilan data. Error API tetap ditampilkan.
- Screenshot dashboard tersedia di `docs/screenshots/dashboard.png` dan README. Capture awal JPEG browser diganti dengan PNG langsung dari renderer, viewport 1440 × 900 pada device scale 2 (2880 × 1800 piksel), tanpa kompresi JPEG. Berkas ini sengaja dipublikasikan; capture lainnya, database, `.env`, tools, cache, dan runtime tetap diabaikan.
- Daftar berkas publik diperiksa terhadap runtime, konfigurasi rahasia, pola token/kunci privat, dan kontak lamaran; tidak ditemukan pola tersebut. Pemeriksaan pola bukan jaminan audit keamanan lengkap.

Dokumen ini membedakan hasil pemeriksaan dari kemampuan yang belum diuji. Angka data bukan fixture aplikasi dan bukan ukuran seluruh pemberitaan Indonesia.

## Baseline implementasi, 8–9 Oktober 2026

- Windows, Bun, uv/Python 3.12, Java 21, Maven 3.9, PostgreSQL 17.
- Backend Spring Boot, frontend React/TypeScript/Vite, collector httpx/psycopg.
- Pengujian backend asisten dan API: **16 tests lulus**, mencakup filter/input, Origin/JSON, write guard, kutipan tidak valid, database kosong, kegagalan web, decoder JSON/SSE/gzip, respons terpotong, batas ukuran, dan URL hasil pencarian.
- Pipeline: **6 tests lulus** dengan database test terpisah pada baseline; Ruff lulus. Meliputi parser, transaksi, deduplikasi, rollback, pengisian gap, dan respons 429 tiruan.
- Build TypeScript/Vite dan paket backend berhasil.
- Pengambilan GDELT langsung sebelumnya menghasilkan 267 URL dari 38 domain dalam cakupan media Indonesia. Tidak ada snapshot database atau data pribadi yang didistribusikan bersama source code.
- Asisten diuji dengan gateway 9router dan model yang tersedia: jawaban statistik sesuai dashboard; pertanyaan kasus Rafale menjawab berdasarkan judul dan mengutip 1 URL dari 1 domain.
- Pencarian web diuji menggunakan respons tiruan. Gateway saat pemeriksaan belum memiliki penyedia pencarian aktif; **pencarian web live belum terverifikasi**.

## Batas verifikasi

Belum ada klaim load test, deployment publik aman, login multi-tenant, klasifikasi sentimen Indonesia terlatih, pengelompokan event, ranking kasus nasional, atau kebenaran setiap klaim AI. Validasi ID kutipan hanya memeriksa identitas bukti. Perubahan upstream dapat memerlukan verifikasi ulang.

## Rilis branding open source

- Pemeriksaan ulang 9 Oktober 2026: **6 Python passed**, Ruff lulus, **16 Java tests** lulus, build paket backend berhasil, TypeScript/Vite berhasil. Output JavaScript 255,35 kB, gzip 79,35 kB.
- Eksekusi gabungan pertama terhenti karena batas waktu proses 120 detik saat pengujian Java belum selesai; backend kemudian diuji dan dipaketkan pada proses terpisah dengan batas waktu memadai. Tidak ada kegagalan assertion pada putaran akhir.
- Browser desktop dan mobile 390×844 menampilkan branding Indomonitor serta data API. Capture mobile diperiksa secara visual: navigasi horizontal dapat digulir, filter tersusun vertikal, kartu ringkasan memakai dua kolom. Ini pemeriksaan dashboard, bukan seluruh alur di semua perangkat.
- Ikon dan logo SVG berisi vektor biru-merah tanpa latar, gambar raster tertanam, script, atau resource eksternal. Wordmark memakai font fallback sistem; bentuk huruf dapat sedikit berbeda antar perangkat.
- Informasi batas data dipusatkan pada bagian `Tentang data`; metode kemiripan dan riwayat sumber tersedia ketika dibuka. Peringatan AI dan status kegagalan tidak dihilangkan.
- Source baru menggunakan namespace Java `id.indomonitor`, tanpa referensi konteks pekerjaan pada source/dokumentasi. Folder konfigurasi privat, database, runtime, cache, screenshot pemeriksaan, dan hasil build diabaikan Git.
- Pengujian memakai database test terpisah. Tidak menjalankan collector atau memasukkan fixture ke database aplikasi pada rilis branding ini.
