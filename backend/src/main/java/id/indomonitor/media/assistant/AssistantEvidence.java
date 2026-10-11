package id.indomonitor.media.assistant;

import id.indomonitor.media.api.QueryFilter;
import id.indomonitor.media.data.NewsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.time.Duration;
import java.util.*;

/** Angka dan sampel dibaca dari satu snapshot database. */
@Service
public class AssistantEvidence {
    public record Snapshot(Map<String, Object> current, Map<String, Object> previous,
        Map<String, Object> health, List<Map<String, Object>> sources) {}
    private final NewsRepository repository;
    public AssistantEvidence(NewsRepository repository) { this.repository = repository; }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Snapshot collect(QueryFilter filter, String keyword) {
        var previousFilter = new QueryFilter(filter.from().minus(Duration.between(filter.from(), filter.to())),
            filter.from(), filter.query(), filter.domain(), filter.scope());
        var current = compact(repository.analytics(filter));
        var previous = compact(repository.analytics(previousFilter));
        List<Map<String, Object>> sources = new ArrayList<>();
        sources.add(statistic("A1", "Statistik periode aktif", filter, current));
        sources.add(statistic("A2", "Statistik periode sebelumnya", previousFilter, previous));
        Set<Object> seen = new HashSet<>();
        addArticles(sources, seen, repository.query("SELECT id,url,title,domain,first_seen_at,themes FROM articles WHERE "
            + filter.where() + " ORDER BY first_seen_at DESC,id DESC LIMIT 8", filter.arguments().toArray()));
        Object themes = current.get("themes");
        if (themes instanceof List<?> rows) {
            for (Object row : rows.stream().limit(5).toList()) {
                String theme = String.valueOf(((Map<?, ?>) row).get("theme"));
                var args = filter.arguments(); args.add(theme);
                addArticles(sources, seen, repository.query("SELECT id,url,title,domain,first_seen_at,themes FROM articles WHERE "
                    + filter.where() + " AND jsonb_exists(themes, ?) ORDER BY (title IS NOT NULL) DESC,first_seen_at DESC,id DESC LIMIT 2", args.toArray()));
            }
        }
        if (!keyword.isBlank()) {
            var args = filter.arguments();
            String pattern = "%" + keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            args.add(pattern); args.add(pattern); args.add(pattern);
            String where = filter.where() + " AND (COALESCE(title,'') ILIKE ? ESCAPE '\\' OR url ILIKE ? ESCAPE '\\' OR themes::text ILIKE ? ESCAPE '\\')";
            var counts = repository.one("SELECT count(*) AS total,count(DISTINCT domain) AS outlets FROM articles WHERE " + where, args.toArray());
            sources.add(Map.of("id", "A3", "kind", "statistics", "title", "Pencarian tambahan dalam filter aktif",
                "keyword", keyword, "data", counts, "from", filter.from().toString(), "to", filter.to().toString()));
            addArticles(sources, seen, repository.query("SELECT id,url,title,domain,first_seen_at,themes FROM articles WHERE "
                + where + " ORDER BY (title IS NOT NULL) DESC,first_seen_at DESC,id DESC LIMIT 8", args.toArray()));
        }
        return new Snapshot(current, previous, repository.health(), sources);
    }

    private Map<String, Object> compact(Map<String, Object> analytics) {
        Map<String, Object> result = new LinkedHashMap<>(analytics);
        // Timeline tetap ditampilkan dashboard; asisten memakai agregat agar konteks tidak membengkak.
        result.remove("timeline");
        return result;
    }
    private Map<String, Object> statistic(String id, String title, QueryFilter filter, Map<String, Object> data) {
        return Map.of("id", id, "kind", "statistics", "title", title, "from", filter.from().toString(),
            "to", filter.to().toString(), "query", filter.query(), "domain", filter.domain(), "scope", filter.scope(), "data", data);
    }
    private void addArticles(List<Map<String, Object>> sources, Set<Object> seen, List<Map<String, Object>> articles) {
        for (var article : articles) {
            if (!seen.add(article.get("id"))) continue;
            Map<String, Object> source = new LinkedHashMap<>(article);
            source.put("articleId", article.get("id")); source.put("id", "B" + article.get("id")); source.put("kind", "article");
            if (article.get("title") instanceof String title) source.put("title", WebSearchClient.limit(title, 400));
            if (article.get("themes") instanceof List<?> themes) source.put("themes", themes.stream().limit(20).toList());
            sources.add(source);
        }
    }
}
