package id.indomonitor.media.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class GatewayClientTest {
    private final ObjectMapper json = new ObjectMapper();
    @Test void decodesJsonAndSseButRejectsTruncation() throws Exception {
        assertEquals("Jawaban", GatewayClient.completion("{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"Jawaban\"}}]}", json));
        String sse = "data: {\"choices\":[{\"delta\":{\"content\":\"Jawaban\"},\"finish_reason\":null}]}\n\ndata: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n";
        assertEquals("Jawaban", GatewayClient.completion(sse, json));
        assertThrows(IOException.class, () -> GatewayClient.completion(sse.replace("\"stop\"", "\"length\""), json));
        assertThrows(IOException.class, () -> GatewayClient.completion("{\"choices\":[{\"finish_reason\":\"length\"}]}", json));
    }
    @Test void handlesGzipAndBoundsResponseSize() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var bytes = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(bytes)) { gzip.write("{\"ok\":true}".getBytes(StandardCharsets.UTF_8)); }
        server.createContext("/gzip", exchange -> { byte[] data = bytes.toByteArray(); exchange.sendResponseHeaders(200, data.length); exchange.getResponseBody().write(data); exchange.close(); });
        server.createContext("/large", exchange -> { byte[] data = new byte[262145]; exchange.sendResponseHeaders(200, data.length); exchange.getResponseBody().write(data); exchange.close(); });
        server.start();
        try {
            var gateway = new GatewayClient(json); String base = "http://127.0.0.1:" + server.getAddress().getPort();
            assertEquals("{\"ok\":true}", gateway.post(base, "/gzip", "", Map.of(), 2));
            assertThrows(ResponseStatusException.class, () -> gateway.post(base, "/large", "", Map.of(), 2));
        } finally { server.stop(0); }
    }
    @Test void searchFiltersUnsafeUrlsAndReportsMissingConfiguration() {
        assertTrue(WebSearchClient.publicLink("https://www.kompas.com/news"));
        for (String url : List.of("javascript:alert(1)", "http://127.0.0.1/a", "http://localhost/a", "http://machine.local/a", "https://user:pass@site.com/a"))
            assertFalse(WebSearchClient.publicLink(url));
        var web = new WebSearchClient(new GatewayClient(json), json, "http://127.0.0.1:1", "", "");
        assertEquals("unconfigured", web.search("berita", true).status());
        assertEquals("disabled", web.search("berita", false).status());
    }
    @Test void searchUsesOnlyRealUniquePublicResultsAndReportsFailure() {
        var gateway = mock(GatewayClient.class);
        when(gateway.post(anyString(), eq("/search"), anyString(), any(), eq(18))).thenReturn("""
            {"results":[
              {"title":"Berita","url":"https://www.kompas.com/news","snippet":"Cuplikan nyata"},
              {"title":"Duplikat","url":"https://www.kompas.com/news","snippet":"Duplikat"},
              {"title":"Lokal","url":"http://127.0.0.1/news","snippet":"Tidak aman"},
              {"title":"Tanpa cuplikan","url":"https://www.example.com/news"}
            ],"errors":[]}
            """);
        var web = new WebSearchClient(gateway, json, "http://127.0.0.1:20128/v1", "key", "search-provider");
        var result = web.search("berita", true);
        assertEquals("success", result.status()); assertEquals(1, result.sources().size());
        assertEquals("W1", result.sources().getFirst().get("id"));
        assertEquals("Cuplikan nyata", result.sources().getFirst().get("text"));
        when(gateway.post(anyString(), eq("/search"), anyString(), any(), eq(18))).thenThrow(new IllegalStateException());
        assertEquals("failed", web.search("berita", true).status());
        assertTrue(web.search("berita", true).sources().isEmpty());
    }
}
