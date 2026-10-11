package id.indomonitor.media.analysis;

import id.indomonitor.media.api.QueryFilter;
import id.indomonitor.media.data.NewsRepository;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.Map;

@Service
public class ReportService {
    private final NewsRepository repository;
    public ReportService(NewsRepository repository) { this.repository = repository; }

    public String report(QueryFilter filter) {
        var analytics = repository.analytics(filter);
        var summary = (Map<?, ?>) analytics.get("summary");
        var items = repository.evidenceCandidates(filter);
        StringBuilder text = new StringBuilder("# Laporan pemantauan pemberitaan\n\n");
        text.append("Dibuat: ").append(Instant.now()).append("\n\n")
            .append("Periode observasi: ").append(filter.from()).append(" sampai sebelum ").append(filter.to()).append("\n\n")
            .append("Cakupan: ").append(filter.scope()).append(". Filter: ").append(plain(filter.query())).append("\n\n")
            .append("URL unik terkumpul: ").append(summary.get("total")).append("\n\n")
            .append("Domain sumber: ").append(summary.get("outlets")).append("\n\n")
            .append("Rata-rata V2Tone: ").append(summary.get("mean_tone")).append("; metadata bernada: ").append(summary.get("tone_count")).append("\n\n")
            .append("## Bukti judul terbaru (maksimal 20)\n\n");
        items.stream().limit(20).forEach(item -> text.append("- ").append(plain((String) item.get("title")))
            .append(" | ").append(item.get("domain")).append(" | ").append(item.get("first_seen_at"))
            .append("\n  Sumber: ").append(item.get("url")).append("\n"));
        if (items.isEmpty()) text.append("Judul tidak tersedia. Telusuri URL pada dashboard.\n");
        text.append("\n## Metode dan batasan\n\n")
            .append("Count memakai URL unik yang tersimpan, bukan total seluruh pemberitaan Indonesia. Waktu adalah observasi GDELT, bukan waktu publikasi. ")
            .append("V2Tone adalah pengukuran otomatis GDELT, bukan opini masyarakat atau sentimen terhadap entitas tertentu. ")
            .append("Gap pengambilan bukan bukti nol aktivitas. Berita yang menyebut Indonesia tidak otomatis berasal dari media Indonesia.\n\n")
            .append("Sumber: GDELT Project, https://www.gdeltproject.org/ . Artikel tetap milik penerbit masing-masing.\n");
        return text.toString();
    }

    private String plain(String value) {
        return value.replaceAll("[\\r\\n\\[\\]<>*`#]", " ");
    }
}
