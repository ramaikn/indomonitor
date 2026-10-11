package id.indomonitor.media.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import id.indomonitor.media.api.QueryFilter;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AssistantServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private final QueryFilter filter = new QueryFilter(Instant.parse("2026-10-08T00:00:00Z"),
        Instant.parse("2026-10-09T00:00:00Z"), "", "", "indonesian_outlet");
    @Test void rejectsUnknownCitationsAndUncitedClaims() throws Exception {
        var sources = List.<Map<String, Object>>of(Map.of("id", "A1"));
        for (String citations : List.of("[]", "[\"W99\"]", "[123]")) {
            var answer = json.readTree("{\"status\":\"answered\",\"claims\":[{\"text\":\"Klaim\",\"citations\":" + citations + "}],\"limitation\":\"Terbatas\"}");
            assertThrows(ResponseStatusException.class, () -> AssistantService.validate(answer, sources));
        }
    }
    @Test void acceptsPartialAndInsufficientResponses() throws Exception {
        var sources = List.<Map<String, Object>>of(Map.of("id", "A1"));
        assertEquals("partial", AssistantService.validate(json.readTree("{\"status\":\"partial\",\"claims\":[{\"text\":\"Ada 3 URL\",\"citations\":[\"A1\"]}],\"limitation\":\"Bukan semua berita\"}"), sources).get("status"));
        assertEquals("insufficient", AssistantService.validate(json.readTree("{\"status\":\"insufficient\",\"claims\":[],\"limitation\":\"Bukti belum tersedia\"}"), sources).get("status"));
    }
    @Test void keepsDatabaseAnswerWhenWebFails() throws Exception {
        var ai = mock(AiClient.class); var web = mock(WebSearchClient.class); var evidence = mock(AssistantEvidence.class);
        when(ai.answer(anyString(), any())).thenReturn(json.readTree("{\"keyword\":\"\",\"webQuery\":\"tren Indonesia\"}"),
            json.readTree("{\"status\":\"partial\",\"claims\":[{\"text\":\"Ada 3 URL terkumpul\",\"citations\":[\"A1\"]}],\"limitation\":\"Web gagal\"}"));
        when(ai.model()).thenReturn("test");
        var stats = Map.<String, Object>of("summary", Map.of("total", 3));
        when(evidence.collect(filter, "")).thenReturn(new AssistantEvidence.Snapshot(stats, stats, Map.of(), List.of(Map.of("id", "A1"))));
        when(web.search("tren Indonesia", true)).thenReturn(new WebSearchClient.Result("failed", "Web gagal", "tren Indonesia", List.of()));
        var result = new AssistantService(ai, web, evidence).ask("Tren?", filter, true, List.of());
        assertEquals("partial", result.get("status"));
        assertEquals("failed", ((Map<?, ?>) result.get("web")).get("status"));
        verify(evidence).collect(filter, "");
    }
    @Test void emptyDatabaseDoesNotGenerateFacts() throws Exception {
        var ai = mock(AiClient.class); var web = mock(WebSearchClient.class); var evidence = mock(AssistantEvidence.class);
        when(ai.answer(anyString(), any())).thenReturn(json.readTree("{\"keyword\":\"\",\"webQuery\":\"tren\"}"));
        when(ai.model()).thenReturn("test");
        var stats = Map.<String, Object>of("summary", Map.of("total", 0));
        when(evidence.collect(filter, "")).thenReturn(new AssistantEvidence.Snapshot(stats, stats, Map.of(), List.of(Map.of("id", "A1"))));
        when(web.search("tren", false)).thenReturn(new WebSearchClient.Result("disabled", "Tidak digunakan", "", List.of()));
        assertEquals("insufficient", new AssistantService(ai, web, evidence).ask("Tren?", filter, false, List.of()).get("status"));
        verify(ai, times(1)).answer(anyString(), any());
    }
}
