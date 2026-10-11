# Peta sistem

## Lokasi kerja dan identitas

- Repo utama adalah `ramaikn/indomonitor`, terpisah dari repo lamaran dan Semantree. Mulai pekerjaan dari root checkout ini.
- Versi `project/media-intelligence` di repo portofolio adalah arsip, bukan sumber pengembangan utama. Jangan menjalankannya untuk memeriksa branding baru.
- Logo/favicon: `frontend/public/indomonitor-*.svg`; halaman: `frontend/src/App.tsx`; metadata: `frontend/index.html`.
- Setup: `scripts/setup.ps1`; layanan: `start-database.ps1`, `start-backend.ps1`, `start-frontend.ps1`. Backend menolak port yang sudah terpakai sebelum build.
- `.env` dibaca sebelum lokasi tools dipilih. `INDOMONITOR_TOOLS_DIR` opsional dapat memakai tools lokal bersama tanpa menyalin instalasi.
- Git: branch utama `main`, remote `origin` ke repo Indomonitor, pemeriksaan otomatis di `.github/workflows/checks.yml`. Lisensi Apache 2.0 dipertahankan dari checkout Indomonitor yang sudah ada, bukan diganti mengikuti MIT Semantree.
- Branding tidak mengubah nama database, identitas pipeline, atau checksum migrasi. `.runtime/pgdata` adalah data lokal, bukan bagian Git.

## Tanggung jawab modul

| Bagian | Lokasi utama | Tanggung jawab |
| --- | --- | --- |
| Adapter | `pipeline/.../client.py`, `cli.py` | Manifest/ZIP/DOC, timeout, retry, pembatasan ukuran, jadwal CLI |
| Kontrak data | `pipeline/.../normalize.py` | Identitas URL, cakupan Indonesia, waktu, tema/nada; judul nullable |
| Transaksi | `pipeline/.../store.py` | Run, quarantine, upsert, observasi, lock, checkpoint setelah commit |
| Schema | `database/V*.sql` | Migrasi Flyway bersama; tanpa schema tersembunyi di Python |
| Query | `backend/.../data/NewsRepository.java` | Filter terparameter, pagination stabil, agregasi SQL, provenance |
| Analisis | `backend/.../analysis/` | Kemiripan judul dan laporan deterministik berbukti |
| Asisten | `backend/.../assistant/` | Rencana pencarian AI, snapshot statistik/bukti, gateway chat/pencarian, validasi kutipan |
| HTTP | `backend/.../api/` | Validasi request, guard write lokal, respons error aman |
| UI | `frontend/src/` | State aktual, filter, grafik, bukti, error/empty/loading |

## Alur commit pipeline

1. Ambil advisory lock PostgreSQL agar collector lokal tidak menulis bersamaan.
2. Catat run `running` dalam transaksi singkat yang sudah commit.
3. Jika arsip sudah memiliki checkpoint, catat `skipped`.
4. Ambil respons sumber dengan batas waktu/ukuran.
5. Dalam satu transaksi: validasi baris, simpan metadata, observasi run, quarantine, status final, checkpoint.
6. Kegagalan membatalkan artikel dan checkpoint; status run disimpan `failed` di luar transaksi rollback.

Jika proses dihentikan paksa sebelum finalisasi, run dapat tertinggal `running`, tetapi tidak ada checkpoint yang mendahului commit. Pengambilan berikutnya masih aman. Run tersebut perlu dipahami sebagai proses tidak selesai, bukan dianggap success.

## Entitas

```text
ingestion_runs ──< article_observations >── articles
       │
       ├──< quarantines
       └──< checkpoints (adapter, artifact unik)
watchlists (topik/ambang persisten, tidak mengubah data sumber)
```

`articles.url` unik. Parameter query terikat driver, bukan interpolasi SQL. Timestamp `timestamptz`; agregasi UTC, tampilan WIB. `first_seen_at` dapat mundur bila arsip lama ditemukan, `last_seen_at` dapat maju. Nilai kosong tidak menjadi nol atau tanggal palsu.

## HTTP

| Endpoint | Fungsi |
| --- | --- |
| `GET /api/health` | Database, run terakhir, run sukses terakhir, waktu pemeriksaan |
| `GET /api/articles` | `from`, `to` (ISO UTC, awal inklusif/akhir eksklusif), `q`, `domain`, `scope`, `page`, `size` |
| `GET /api/articles/{id}` | Metadata beserta observasi sumber/run |
| `GET /api/analytics` | Ringkasan, timeline, sumber, tema, distribusi nada, definisi metode |
| `GET /api/evidence` | Kemiripan judul dari maksimal 500 kandidat terbaru |
| `GET /api/runs` | Maksimal 40 run terbaru |
| `GET /api/report` | Markdown dengan periode, denominator, bukti dan keterbatasan |
| `GET /api/watchlists` | Daftar topik persisten |
| `POST /api/watchlists` | Tambah topik dengan `X-Admin-Key` |
| `DELETE /api/watchlists/{id}` | Hapus topik dengan `X-Admin-Key` |
| `GET /api/alerts` | Evaluasi ambang URL teramati 24 jam, bukan diagnosis krisis |
| `GET /api/assistant/status` | Kesiapan konfigurasi AI/web dan informasi pengiriman data, tanpa secret |
| `POST /api/assistant` | Pertanyaan, filter aktif, `useWeb`, maksimal enam pesan riwayat; wajib JSON dan Origin yang diizinkan |

Ukuran halaman 1..100. Rentang query maksimal 366 hari. Pencarian cocok pada judul/URL/tema, bukan seluruh isi artikel. Filter `scope=all` harus dipilih eksplisit. Default `indonesian_outlet`.

## Keputusan teknis

- Satu API dan satu collector, tidak memakai microservices, Kafka, Redis, atau GPU tanpa kebutuhan nyata.
- PostgreSQL menjadi sumber kebenaran; browser tidak mengarang aggregasi dari sampel halaman.
- Laporan tidak memerlukan LLM, sehingga setiap angka dapat ditelusuri ke query.
- Asisten memakai LLM untuk rencana pencarian dan penjelasan saja; statistik tetap SQL. Tidak menerima SQL, endpoint, atau tindakan dari model.
- Nada berasal dari penyedia dengan provenance. Belum ada classifier sentimen Indonesia hasil eksperimen proyek.
- Analisis judul memakai aturan yang dapat dijelaskan, bukan menyimpulkan motif media.
- Lockfile Bun dan uv disimpan. Runtime, secret, hasil build, dan database diabaikan Git.
- Backend dijalankan dari `.runtime/application.jar`, bukan `target`, agar Windows tidak mengunci hasil build Maven saat aplikasi berjalan.

## Alur asisten

`AssistantController` memvalidasi pertanyaan maksimal 1.200 karakter, periode/filter, dan riwayat. `LocalWriteGuard` mengizinkan POST asisten dari `FRONTEND_ORIGIN` atau Origin backend yang sama, hanya JSON; watchlist tetap memerlukan kunci admin. Ini guard lokal, bukan autentikasi pengguna.

`AssistantService` membatasi dua percakapan aktif. `AiClient` menggunakan model OpenAI-compatible untuk menghasilkan kata kunci maksimal 100 karakter dan query web maksimal 300 karakter. Riwayat dipakai memahami pertanyaan lanjutan, bukan menjadi sumber fakta.

`AssistantEvidence` memakai transaksi PostgreSQL repeatable-read untuk membaca snapshot periode aktif, periode sebelumnya dengan panjang sama, status pengambilan, delapan berita terbaru, dua contoh untuk lima tema teratas, serta delapan hasil kata kunci. Statistik A1/A2/A3 dapat dibuka di UI; berita memakai ID B. Filter dashboard tidak ditimpa rencana model. Keyword tambahan merupakan substring satu frasa, bukan pencarian semantik penuh.

`WebSearchClient` memanggil `SEARCH_BASE_URL/search` jika diminta dan `SEARCH_MODEL` terisi. Maksimal enam hasil ber-ID W, cuplikan 1.600 karakter, URL publik HTTP/HTTPS. Tidak mengambil URL artikel. Status disabled/unconfigured/failed/empty/partial/success ditampilkan; tidak ada hasil buatan pengganti.

`AiClient` meminta JSON berisi claims/kutipan/status/limitation. Backend menolak kutipan di luar daftar bukti, klaim tanpa kutipan, dan jawaban terpotong. Pemeriksaan ini memastikan identitas sumber, **bukan pembuktian otomatis bahwa setiap kalimat didukung sumber**. Kebenaran angka/penjelasan model tetap perlu ditinjau.

`GatewayClient` menyediakan HTTP timeout, batas total request, dua permintaan keluar bersamaan, batas respons terurai 256 KiB, tanpa redirect, dan decoder JSON/SSE/gzip. Satu percakapan maksimal dua permintaan chat dan satu pencarian; tidak ada retry otomatis yang menggandakan biaya. Snapshot database dilepas sebelum panggilan web/jawaban.

Frontend memberi waktu tunggu maksimal 185 detik, menampilkan error/loading, menyimpan percakapan hanya di memori, dan membatalkan request saat filter berubah. Pembatalan browser tidak menjamin permintaan yang sudah dikirim ke penyedia dibatalkan.

## Lokasi dan status antarmuka

Checkout utama berada di `project/indomonitor` dalam workspace lamaran. Repo Git sendiri berisi frontend, backend, pipeline, migrasi, dan docs; repo induk mengabaikan checkout ini. `.env`, database, dan tools tetap lokal. Nama dan logo publik adalah Indomonitor.

Dashboard menampilkan waktu data terakhir tanpa peringatan kegagalan collector. Status freshness, kegagalan/partial, dan riwayat tetap tersedia di tab Pengambilan data. Error API yang menghalangi pembacaan data tetap tampil; data gagal tidak diganti dengan data buatan.

## Pengembangan berikutnya

Login/tenant dan deployment, kontrak sumber tambahan untuk judul/body, annotation/evaluasi sentimen domain berita, pengelompokan event terukur, serta integrasi sosial berizin. Jangan mengubah sumber atau menambah model berbayar secara diam-diam.
