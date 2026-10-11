# Kontribusi

## Sebelum mengubah kode

- Baca [README](README.md), [peta sistem](docs/architecture.md), dan [keamanan](docs/security.md).
- Untuk perubahan perilaku atau sumber data, jelaskan masalah, cakupan, risiko, dan cara memeriksanya pada issue atau pull request.
- Gunakan Bun untuk frontend dan uv untuk Python; pertahankan lockfile.
- Tidak mengisi aplikasi dengan berita contoh, menutupi kegagalan sumber, atau mengubah metadata kosong menjadi fakta buatan.

## Pemeriksaan

Jalankan `scripts/test.ps1` di Windows dengan database lokal. Script memakai database test terpisah yang diisi ulang. Alternatif: `mvn test` pada backend, `bun run build` pada frontend, serta `uv run pytest -q` dan `uv run ruff check src tests` pada pipeline.

Batasi perubahan pada satu tanggung jawab dan tambahkan pengujian untuk alur kritis. Dokumentasikan perubahan API, konfigurasi, hak sumber, atau batas analisis. Jika pemeriksaan tertentu belum dilakukan, tuliskan apa yang belum diverifikasi.

## Pull request

Repo memakai branch utama `main`. Buat branch terarah seperti `fix/source-status`, commit perubahan terkait saja, lalu ajukan pull request ke `main`. Workflow `Pemeriksaan` menjalankan build frontend, JUnit, serta pengujian pipeline dengan PostgreSQL sementara. Tidak memakai database atau secret aplikasi. CI GitHub belum dianggap lulus sebelum run benar-benar selesai.

- Jelaskan tujuan, perubahan yang terlihat, hasil pengujian, dan batas yang tersisa.
- Jangan kirim `.env`, kredensial, database, log privat, metadata pengguna, atau hasil AI yang mengandung rahasia.
- Sertakan screenshot bila UI berubah dan screenshot benar-benar berasal dari aplikasi.
- Gunakan pesan commit Conventional Commits, misalnya `feat(assistant): add source filters` atau `fix(pipeline): preserve ingestion checkpoints`.
- Jangan mengklaim hubungan dengan penerbit atau penyedia data yang tidak ada.

Dengan mengirim kontribusi, Anda menyetujui bahwa kontribusi Anda tersedia di bawah Apache License 2.0 yang digunakan proyek. Dependensi dan data pihak ketiga tetap mengikuti ketentuannya sendiri.
