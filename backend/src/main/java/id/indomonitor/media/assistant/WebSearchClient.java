package id.indomonitor.media.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.time.Instant;
import java.util.*;

@Service
public class WebSearchClient {
    public record Result(String status, String message, String query, List<Map<String, Object>> sources) {}
    private final GatewayClient gateway;
    private final ObjectMapper json;
    private final String url, key, model;
    public WebSearchClient(GatewayClient gateway, ObjectMapper json, @Value("${media.search-url}") String url,
        @Value("${media.search-key}") String key, @Value("${media.search-model}") String model) {
        this.gateway = gateway; this.json = json; this.url = url; this.key = key; this.model = model;
    }
    public boolean configured() { return !url.isBlank() && !model.isBlank(); }
    public Result search(String question, boolean enabled) {
        if (!enabled) return new Result("disabled", "Pencarian web tidak digunakan.", "", List.of());
        if (!configured()) return new Result("unconfigured", "Layanan pencarian web belum dikonfigurasi. Jawaban hanya menggunakan database.", "", List.of());
        String query = question.strip();
        try {
            var body = Map.of("model", model, "query", query, "max_results", 6);
            var root = json.readTree(gateway.post(url, "/search", key, body, 18));
            if (root == null || !root.path("results").isArray()) throw new IllegalArgumentException();
            List<Map<String, Object>> sources = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (var item : root.path("results")) {
                String link = item.path("url").asText("");
                if (!publicLink(link) || !seen.add(link)) continue;
                String snippet = item.path("snippet").asText("");
                if (snippet.isBlank()) continue;
                sources.add(Map.of("id", "W" + (sources.size() + 1), "kind", "web", "url", link,
                    "title", limit(item.path("title").asText(link), 240), "text", limit(snippet, 1600),
                    "publishedAt", limit(item.path("published_at").asText(""), 80), "retrievedAt", Instant.now().toString()));
                if (sources.size() == 6) break;
            }
            boolean errors = root.path("errors").isArray() && !root.path("errors").isEmpty();
            String status = sources.isEmpty() ? (errors ? "failed" : "empty") : (errors ? "partial" : "success");
            String message = sources.isEmpty() ? "Tidak ada hasil web yang dapat dipakai. Jawaban hanya menggunakan database."
                : errors ? "Sebagian pencarian gagal; kutipan web yang tersedia tetap ditampilkan." : "Hasil web adalah konteks tambahan, bukan ukuran volume pemberitaan.";
            return new Result(status, message, query, sources);
        } catch (Exception ex) {
            return new Result("failed", "Pencarian web gagal. Jawaban hanya menggunakan database; tidak ada hasil web pengganti.", query, List.of());
        }
    }
    static String limit(String text, int max) { return text.length() > max ? text.substring(0, max) : text; }
    static boolean publicLink(String link) {
        try {
            URI uri = URI.create(link); String host = uri.getHost();
            return Set.of("http", "https").contains(uri.getScheme()) && host != null && uri.getUserInfo() == null
                && host.contains(".") && !host.matches("[0-9.]+") && !host.endsWith(".localhost")
                && !host.endsWith(".local") && !host.endsWith(".internal");
        } catch (Exception ex) { return false; }
    }
}
