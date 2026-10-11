package id.indomonitor.media.analysis;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SimilarityServiceTest {
    private final SimilarityService service = new SimilarityService();

    @Test void tokenSimilarityIsExplainable() {
        assertEquals(1, service.jaccard(service.tokens("Layanan Kereta Cepat Jakarta Bandung"), service.tokens("layanan kereta cepat Jakarta Bandung")));
        assertEquals(0, service.jaccard(Set.of(), Set.of("kereta")));
    }

    @Test void sameOutletIsNotCrossMediaEvidence() {
        Map<String,Object> first = Map.of("id",1,"title","Layanan kereta cepat Jakarta Bandung meningkat", "domain","example.test", "first_seen_at","2026-10-08T01:00:00Z");
        Map<String,Object> second = Map.of("id",2,"title","Layanan kereta cepat Jakarta Bandung meningkat", "domain","example.test", "first_seen_at","2026-10-08T02:00:00Z");
        assertEquals(0, service.analyze(List.of(first, second)).get("pairCount"));
    }
}
