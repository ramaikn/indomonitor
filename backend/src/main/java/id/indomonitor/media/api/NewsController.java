package id.indomonitor.media.api;

import id.indomonitor.media.analysis.ReportService;
import id.indomonitor.media.analysis.SimilarityService;
import id.indomonitor.media.data.NewsRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RestController
@RequestMapping("/api")
public class NewsController {
    private final NewsRepository repository;
    private final SimilarityService similarity;
    private final ReportService reports;

    public NewsController(NewsRepository repository, SimilarityService similarity, ReportService reports) {
        this.repository = repository; this.similarity = similarity; this.reports = reports;
    }

    private QueryFilter filter(Map<String, String> params) {
        Instant to = params.containsKey("to") ? Instant.parse(params.get("to")) : Instant.now();
        Instant from = params.containsKey("from") ? Instant.parse(params.get("from")) : to.minus(7, ChronoUnit.DAYS);
        return new QueryFilter(from, to, params.get("q"), params.get("domain"), params.get("scope"));
    }

    @GetMapping("/articles")
    public Map<String, Object> articles(@RequestParam Map<String, String> params) {
        int page = Integer.parseInt(params.getOrDefault("page", "0"));
        int size = Integer.parseInt(params.getOrDefault("size", "20"));
        if (page < 0 || page > 10000 || size < 1 || size > 100) throw new IllegalArgumentException("Pagination tidak valid.");
        return repository.articles(filter(params), page, size);
    }

    @GetMapping("/articles/{id}")
    public Map<String, Object> article(@PathVariable long id) {
        var result = repository.article(id);
        if (result.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Artikel tidak ditemukan.");
        return result;
    }

    @GetMapping("/analytics")
    public Map<String, Object> analytics(@RequestParam Map<String, String> params) { return repository.analytics(filter(params)); }

    @GetMapping("/evidence")
    public Map<String, Object> evidence(@RequestParam Map<String, String> params) {
        return similarity.analyze(repository.evidenceCandidates(filter(params)));
    }

    @GetMapping("/runs")
    public List<Map<String, Object>> runs() { return repository.runs(); }

    @GetMapping("/health")
    public Map<String, Object> health() { return repository.health(); }

    @GetMapping(value="/report", produces="text/markdown;charset=UTF-8")
    public ResponseEntity<String> report(@RequestParam Map<String, String> params) {
        return ResponseEntity.ok().header("Content-Disposition", "attachment; filename=media-report.md")
            .body(reports.report(filter(params)));
    }

    @GetMapping("/watchlists")
    public List<Map<String, Object>> watchlists() { return repository.watchlists(); }

    public record Watchlist(@NotBlank @Size(max=100) String name, @NotBlank @Size(max=100) String keyword,
                            @Min(1) @Max(10000) int threshold) { }

    @PostMapping("/watchlists")
    public ResponseEntity<Map<String, Object>> createWatchlist(@Valid @RequestBody Watchlist input) {
        return ResponseEntity.status(201).body(repository.createWatchlist(input.name().strip(), input.keyword().strip(), input.threshold()));
    }

    @DeleteMapping("/watchlists/{id}")
    public ResponseEntity<Void> deleteWatchlist(@PathVariable long id) {
        if (!repository.deleteWatchlist(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/alerts")
    public List<Map<String, Object>> alerts() {
        List<Map<String, Object>> alerts = new ArrayList<>();
        Instant to = Instant.now();
        for (var watch : repository.watchlists()) {
            var current = repository.analytics(new QueryFilter(to.minus(24, ChronoUnit.HOURS), to,
                (String) watch.get("keyword"), "", "indonesian_outlet"));
            var summary = (Map<?, ?>) current.get("summary");
            long count = ((Number) summary.get("total")).longValue();
            alerts.add(Map.of("watchlist", watch, "count", count, "triggered", count >= ((Number) watch.get("threshold")).intValue(),
                "period", "24 jam observasi terakhir", "method", "Threshold jumlah URL tersimpan; bukan peringatan krisis atau perkiraan seluruh pemberitaan"));
        }
        return alerts;
    }
}
