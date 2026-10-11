package id.indomonitor.media.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import id.indomonitor.media.api.QueryFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

@Repository
public class NewsRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public NewsRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc; this.mapper = mapper;
    }

    private Map<String, Object> row(ResultSet rs, int number) throws SQLException {
        Map<String, Object> value = new LinkedHashMap<>();
        var metadata = rs.getMetaData();
        for (int i = 1; i <= metadata.getColumnCount(); i++) {
            Object item = rs.getObject(i);
            String key = metadata.getColumnLabel(i);
            if (item instanceof java.sql.Timestamp time) item = time.toInstant().toString();
            if (metadata.getColumnTypeName(i).equals("jsonb")) {
                try { item = mapper.readValue(rs.getString(i), Object.class); }
                catch (Exception exc) { throw new SQLException("JSON database tidak valid", exc); }
            }
            value.put(key, item);
        }
        return value;
    }

    public List<Map<String, Object>> query(String sql, Object... values) {
        return jdbc.query(sql, this::row, values);
    }

    public Map<String, Object> one(String sql, Object... values) {
        var rows = query(sql, values);
        return rows.isEmpty() ? Map.of() : rows.getFirst();
    }

    public Map<String, Object> articles(QueryFilter filter, int page, int size) {
        var args = filter.arguments();
        long count = jdbc.queryForObject("SELECT count(*) FROM articles WHERE " + filter.where(), Long.class, args.toArray());
        args.add(size); args.add((long) page * size);
        var items = query("SELECT id,url,title,domain,language,scope,first_seen_at,last_seen_at,tone,themes,locations,last_run_id "
            + "FROM articles WHERE " + filter.where() + " ORDER BY first_seen_at DESC,id DESC LIMIT ? OFFSET ?", args.toArray());
        return Map.of("items", items, "total", count, "page", page, "size", size);
    }

    public Map<String, Object> article(long id) {
        var value = one("SELECT * FROM articles WHERE id=?", id);
        if (!value.isEmpty()) {
            value.put("observations", query("SELECT r.id,r.adapter,r.artifact,r.started_at,r.status FROM ingestion_runs r "
                + "JOIN article_observations o ON o.run_id=r.id WHERE o.article_id=? ORDER BY r.started_at DESC LIMIT 50", id));
        }
        return value;
    }

    public Map<String, Object> analytics(QueryFilter filter) {
        var args = filter.arguments().toArray();
        var summary = one("SELECT count(*) AS total,count(DISTINCT domain) AS outlets,count(tone) AS tone_count,"
            + "avg(tone) AS mean_tone,count(title) AS titled,min(first_seen_at) AS earliest,max(last_seen_at) AS latest,"
            + "count(*) FILTER(WHERE scope='indonesian_outlet') AS local_outlets,"
            + "count(*) FILTER(WHERE scope='mentions_indonesia') AS indonesia_mentions FROM articles WHERE " + filter.where(), args);
        var timeline = query("SELECT date_trunc('hour',first_seen_at AT TIME ZONE 'UTC') AT TIME ZONE 'UTC' AS bucket,"
            + "count(*) AS count,avg(tone) AS mean_tone FROM articles WHERE " + filter.where() + " GROUP BY 1 ORDER BY 1", args);
        var sources = query("SELECT domain,count(*) AS count,avg(tone) AS mean_tone FROM articles WHERE "
            + filter.where() + " GROUP BY domain ORDER BY count DESC,domain LIMIT 20", args);
        var themes = query("SELECT theme,count(*) AS count FROM articles CROSS JOIN LATERAL "
            + "jsonb_array_elements_text(themes) AS theme WHERE " + filter.where()
            + " GROUP BY theme ORDER BY count DESC,theme LIMIT 15", args);
        var tones = query("SELECT CASE WHEN tone IS NULL THEN 'unavailable' WHEN tone < -1 THEN 'negative_tone' "
            + "WHEN tone > 1 THEN 'positive_tone' ELSE 'near_zero' END AS label,count(*) AS count FROM articles WHERE "
            + filter.where() + " GROUP BY 1 ORDER BY 1", args);
        return Map.of("summary", summary, "timeline", timeline, "sources", sources, "themes", themes, "tones", tones,
            "method", Map.of("clock", "first_seen_at GDELT, UTC; bukan waktu publikasi", "denominator", "URL unik yang tersimpan dan sesuai filter",
                "tone", "GDELT V2Tone; bukan model sentimen masyarakat", "missingBuckets", "Tidak teramati, bukan nol aktivitas"));
    }

    public List<Map<String, Object>> evidenceCandidates(QueryFilter filter) {
        return query("SELECT id,url,title,domain,first_seen_at FROM articles WHERE " + filter.where()
            + " AND title IS NOT NULL ORDER BY first_seen_at DESC,id DESC LIMIT 500", filter.arguments().toArray());
    }

    public List<Map<String, Object>> runs() {
        return query("SELECT * FROM ingestion_runs ORDER BY started_at DESC,id LIMIT 40");
    }

    public Map<String, Object> health() {
        return Map.of("database", "connected", "articles", one("SELECT count(*) AS count FROM articles").get("count"),
            "lastRun", one("SELECT id,adapter,status,started_at,finished_at,error FROM ingestion_runs ORDER BY started_at DESC LIMIT 1"),
            "lastSuccess", one("SELECT id,adapter,finished_at FROM ingestion_runs WHERE status='success' ORDER BY finished_at DESC LIMIT 1"),
            "checkedAt", Instant.now().toString());
    }

    public List<Map<String, Object>> watchlists() {
        return query("SELECT * FROM watchlists ORDER BY id");
    }

    public Map<String, Object> createWatchlist(String name, String keyword, int threshold) {
        return one("INSERT INTO watchlists(name,keyword,threshold) VALUES(?,?,?) RETURNING *", name, keyword, threshold);
    }

    public boolean deleteWatchlist(long id) {
        return jdbc.update("DELETE FROM watchlists WHERE id=?", id) > 0;
    }
}
