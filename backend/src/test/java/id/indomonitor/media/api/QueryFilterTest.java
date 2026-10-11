package id.indomonitor.media.api;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class QueryFilterTest {
    @Test void escapesWildcardsAndRejectsReversedRange() {
        var start = Instant.parse("2026-10-01T00:00:00Z");
        var end = Instant.parse("2026-10-08T00:00:00Z");
        var filter = new QueryFilter(start, end, "100%_", "", "all");
        assertEquals("%100\\%\\_%", filter.arguments().get(2));
        assertThrows(IllegalArgumentException.class, () -> new QueryFilter(end,start,"","","all"));
        assertThrows(IllegalArgumentException.class, () -> new QueryFilter(start,end,"","","unknown"));
    }
}
