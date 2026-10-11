package id.indomonitor.media.api;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public record QueryFilter(Instant from, Instant to, String query, String domain, String scope) {
    public QueryFilter {
        if (from == null || to == null || !from.isBefore(to) || Duration.between(from, to).toDays() > 366) {
            throw new IllegalArgumentException("Rentang waktu harus berurutan dan maksimal 366 hari.");
        }
        query = query == null ? "" : query.strip();
        domain = domain == null ? "" : domain.strip().toLowerCase();
        scope = scope == null || scope.isBlank() ? "indonesian_outlet" : scope;
        if (query.length() > 100 || domain.length() > 200) {
            throw new IllegalArgumentException("Filter terlalu panjang.");
        }
        if (!List.of("all", "indonesian_outlet", "mentions_indonesia").contains(scope)) {
            throw new IllegalArgumentException("Cakupan sumber tidak valid.");
        }
    }

    public String where() {
        return " first_seen_at >= ? AND first_seen_at < ? "
            + (query.isEmpty() ? "" : " AND (COALESCE(title,'') ILIKE ? ESCAPE '\\' OR url ILIKE ? ESCAPE '\\' OR themes::text ILIKE ? ESCAPE '\\') ")
            + (domain.isEmpty() ? "" : " AND domain = ? ")
            + (scope.equals("all") ? "" : " AND scope = ? ");
    }

    public List<Object> arguments() {
        List<Object> values = new ArrayList<>();
        values.add(java.sql.Timestamp.from(from));
        values.add(java.sql.Timestamp.from(to));
        if (!query.isEmpty()) {
            String pattern = "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            values.add(pattern); values.add(pattern); values.add(pattern);
        }
        if (!domain.isEmpty()) values.add(domain);
        if (!scope.equals("all")) values.add(scope);
        return values;
    }
}
