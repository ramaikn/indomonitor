# Sumber nyata dan hak penggunaan

Pemeriksaan sumber: 8 Oktober 2026.

## GDELT

- Ketentuan resmi: https://www.gdeltproject.org/about.html#termsofuse
- Dokumentasi DOC: https://blog.gdeltproject.org/gdelt-doc-2-0-api-debuts/
- Codebook GKG 2.1: https://data.gdeltproject.org/documentation/GDELT-Global_Knowledge_Graph_Codebook-V2.1.pdf
- Manifest: https://data.gdeltproject.org/gdeltv2/lastupdate.txt
- Manifest translingual: https://data.gdeltproject.org/gdeltv2/lastupdate-translation.txt

Collector default mengambil **original dan translation**. Original banyak berisi berita Inggris; arsip translation memuat berita bahasa Indonesia yang diproses infrastruktur terjemahan GDELT. Mengambil original saja tidak cukup untuk cakupan bahasa Indonesia. Tidak menerjemahkan judul sendiri atau mengklaim bahwa terjemahan penyedia bebas kesalahan.

Ketentuan resmi menyatakan dataset GDELT boleh digunakan tanpa biaya untuk akademik, komersial, atau pemerintah; redistribusi memerlukan atribusi dan tautan ke GDELT. Ini **bukan izin mengambil atau menerbitkan ulang isi penuh artikel dari penerbit**.

Yang disimpan: URL, judul bila disediakan, domain, waktu observasi, tema/lokasi hasil ekstraksi GDELT, skor V2Tone, metadata sumber/run. Tidak menyimpan isi artikel, gambar, profil individu, komentar sosial, atau kredensial.

Arsip hanya diambil dari host GDELT tetap, tidak mengikuti URL artikel. File ZIP dibaca dalam memori tanpa ekstraksi path ke filesystem. Checksum MD5 manifest dipakai sebagai pemeriksaan transfer, bukan bukti keamanan kriptografis atau kebenaran berita. Arsip historis tidak memiliki checksum manifest terbaru, sehingga dibedakan dalam implementasi.

## Dua cakupan berbeda

1. `indonesian_outlet`: domain dalam daftar editorial pada `pipeline/src/media_pipeline/config.py`, termasuk subdomain sah; atau `sourcecountry=Indonesia` dari DOC API yang sudah dibatasi Indonesia.
2. `mentions_indonesia`: media lain yang metadata lokasi GKG-nya menyebut kode FIPS `ID`. Bukan bukti berasal dari Indonesia atau menggunakan bahasa Indonesia.

Filter default hanya media Indonesia. Memilih keduanya harus disengaja dan denominator tetap sesuai filter.

## Batas akses dan kualitas

DOC API saat pemeriksaan memberi `429`. Aplikasi memakai jeda minimum 6 detik dan retry maksimal 3 kali; `Retry-After` berbentuk detik maupun tanggal dihormati sampai 5 menit, dan penundaan lebih lama tercatat sebagai kegagalan untuk dijadwalkan ulang oleh operator. Tidak mengganti IP, menyamarkan klien, atau melewati pembatasan. Arsip GKG merupakan jalur resmi lain dari penyedia yang sama, bukan bypass endpoint DOC. DOC opsional; perolehan arsip tetap berjalan meski DOC dibatasi.

Judul sering tidak tersedia dalam GKG. `PAGE_TITLE`, bila memang disediakan, dipakai apa adanya setelah decoding karakter HTML; tidak mengambil judul dari URL atau menambah judul palsu. DOC API dapat melengkapi metadata melalui pengambilan resmi terpisah.

Dalam pengambilan langsung, ditemukan arsip translingual dengan byte non-UTF-8. Decoder memakai validasi ketat per baris; baris rusak masuk quarantine tanpa replacement karakter, baris valid dapat disimpan. Run menjadi `partial` dan tidak mendapat checkpoint sukses, sehingga tidak menyatakan arsip sepenuhnya berhasil. Waktu batch GDELT dapat sampai 15 menit lebih maju daripada jam pengambilan; parser menerima toleransi batas batch ini tanpa mengubah timestamp.

V2Tone disimpan sebagai skor penyedia (-100..100). UI memakai batas deskriptif -1 dan +1. Tidak ada klaim accuracy, sentimen target, atau opini masyarakat. Tidak dilakukan training pada dataset GDELT tanpa label referensi.

Terbit ulang dan syndication dapat menghasilkan URL berbeda tentang peristiwa sama. Deduplikasi URL tidak menyamakan semua berita itu sebagai satu peristiwa. Kemiripan judul hanya evidence yang perlu ditinjau.

Data lokal dan database tidak masuk Git. Untuk publikasi, periksa hak penggunaan terbaru dan minimisasi metadata kembali. Tidak semua perubahan akses penyedia dapat diantisipasi oleh satu implementasi.
