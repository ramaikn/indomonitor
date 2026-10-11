# Batas keamanan

## Yang diterapkan

- PostgreSQL, backend, dan frontend lokal bind ke `127.0.0.1`.
- Secret acak dibuat dalam `.env`, tidak di-hardcode dan tidak masuk Git.
- API menggunakan role `media_api`: SELECT pada metadata, INSERT/DELETE terbatas watchlist. Tidak dapat menulis artikel/run/checkpoint.
- Migrasi/collector memakai `media_owner`, bukan superuser PostgreSQL.
- Superuser hanya untuk penyiapan database lokal, tidak dipakai oleh API/pipeline.
- Operasi perubahan watchlist memerlukan kunci minimal 32 karakter dengan pemeriksaan constant-time.
- Semua query memakai parameter. Wildcard pencarian pengguna di-escape.
- Konten sumber dirender sebagai teks React, tanpa `dangerouslySetInnerHTML`; URL tampilan hanya HTTP/HTTPS.
- Collector tidak mengunduh URL berita dan tidak menerima host fetch dari pengguna.
- Tidak ada upload publik, eksekusi tindakan LLM, atau endpoint pemicu collector.
- Error API database tidak membocorkan password atau stack trace. Referensi error tersedia untuk log lokal.
- POST asisten menerima JSON dari Origin frontend yang dikonfigurasi atau Origin backend sama. Tidak memerlukan kunci admin karena tidak menulis database; pembatasan dua percakapan dan dua panggilan gateway mengurangi beban lokal, bukan rate limiting pengguna.
- AI/search hanya mengakses endpoint konfigurasi server. Model/pengguna tidak dapat menentukan host tujuan, SQL, atau memicu fetch artikel. Gateway menolak redirect dan membatasi waktu/ukuran respons.
- Secret AI dan pencarian tidak masuk respons status, browser, maupun log error gateway. Jawaban, metadata, dan snippet dirender sebagai teks; ID kutipan diperiksa sebelum dikirim.

## Data yang dikirim ke AI dan pencarian

Pertanyaan, maksimal enam pesan riwayat, statistik, sampel URL/judul/tema, dan informasi freshness **dikirim ke 9router**. Gateway lokal dapat meneruskan data ke penyedia luar; ini bukan model offline. Saat pilihan web dipakai, query hasil rencana AI dikirim ke penyedia pencarian. Kebijakan penyimpanan penyedia belum diaudit. Jangan memasukkan informasi rahasia ke pertanyaan.

Konten berita dan cuplikan web dianggap data tak tepercaya. Prompt melarang mengikuti instruksi dalam sumber, namun tidak menjamin bebas prompt injection atau halusinasi. Tidak ada tindakan LLM selain menyusun kata kunci dan jawaban. Validasi kutipan hanya memastikan ID benar, bukan bahwa klaim terbukti. Pengguna harus memeriksa sumber asli.

## Belum menjadi sistem publik

Kunci admin lokal bukan pengganti login, hak akses multi-tenant, session management, TLS, rotasi secret, rate limiting per pengguna, dan audit tindakan administrasi. Jangan membuka port, membuat tunnel, atau deploy sebelum lapisan ini dipilih dan diuji.

`.env` dan `.runtime/` memerlukan perlindungan akun Windows pemiliknya. Tidak ada jaminan enkripsi disk. Salinan database lokal dapat memuat URL/judul yang menyebut orang, sehingga minimisasi dan retensi perlu ditinjau sebelum distribusi. Tidak ada profil pribadi atau inference sensitif yang dibuat proyek ini.

Installer alat memakai HTTPS. JDK memakai SHA256 dari metadata resmi, Maven SHA512 resmi. Paket PostgreSQL biner dari EnterpriseDB diambil lewat HTTPS; installer ini tidak mengklaim verifikasi hash independen yang belum tersedia. Pembaruan versi dan pemeriksaan kerentanan tetap tanggung jawab pemeliharaan.

Penghentian paksa collector dapat meninggalkan status `running`; tidak boleh disamakan dengan keberhasilan. Database memberikan rollback otomatis untuk transaksi yang belum commit. Periksa riwayat run dan lakukan pengambilan ulang.
