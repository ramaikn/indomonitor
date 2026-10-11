package id.indomonitor.media.assistant;

import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;

/** Komunikasi keluar dibatasi ukuran, waktu, dan jumlah permintaan. */
@Component
public class GatewayClient {
    private final ObjectMapper json;
    private final Semaphore slots = new Semaphore(2);
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
        .version(HttpClient.Version.HTTP_1_1).followRedirects(HttpClient.Redirect.NEVER).build();
    private static final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "gateway-deadline"); thread.setDaemon(true); return thread;
    });

    public GatewayClient(ObjectMapper json) { this.json = json; }

    public String post(String base, String path, String key, Object body, int seconds) {
        if (!slots.tryAcquire()) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Asisten sedang sibuk. Coba sebentar lagi.");
        try {
            URI uri = URI.create(base.replaceAll("/+$", "") + path);
            if (!java.util.Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
                throw new IllegalArgumentException("Endpoint tidak valid");
            var builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(seconds))
                .header("Content-Type", "application/json").header("Accept-Encoding", "identity")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            if (!key.isBlank()) builder.header("Authorization", "Bearer " + key);
            long started = System.nanoTime();
            var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            try (var stream = response.body()) {
                long remaining = Math.max(1, TimeUnit.SECONDS.toNanos(seconds) - (System.nanoTime() - started));
                var deadline = deadlines.schedule(() -> { try { stream.close(); } catch (IOException ignored) {} }, remaining, TimeUnit.NANOSECONDS);
                try {
                    if (response.statusCode() != 200) throw new IOException("Gateway HTTP " + response.statusCode());
                    return decode(stream);
                } finally { deadline.cancel(false); }
            }
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Layanan AI atau pencarian tidak tersedia, menolak permintaan, atau responsnya tidak valid.");
        } finally { slots.release(); }
    }

    private String decode(InputStream stream) throws IOException {
        var probe = new PushbackInputStream(stream, 2);
        byte[] prefix = probe.readNBytes(2); probe.unread(prefix);
        InputStream decoded = prefix.length == 2 && (prefix[0] & 255) == 31 && (prefix[1] & 255) == 139
            ? new java.util.zip.GZIPInputStream(probe) : probe;
        byte[] bytes = decoded.readNBytes(262145);
        if (bytes.length > 262144) throw new IOException("Respons terlalu besar");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static String completion(String raw, ObjectMapper json) throws IOException {
        StringBuilder content = new StringBuilder();
        if (raw.stripLeading().startsWith("data:") || raw.stripLeading().startsWith(":")) {
            for (String line : raw.split("\\R")) {
                if (!line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if (data.isEmpty()) continue;
                if (data.equals("[DONE]")) break;
                var event = json.readTree(data);
                if (event.has("error")) throw new IOException("Stream gagal");
                var choice = event.path("choices").path(0);
                content.append(choice.path("delta").path("content").asText(""));
                if (!choice.path("finish_reason").isMissingNode() && !choice.path("finish_reason").isNull()) {
                    if (!choice.path("finish_reason").asText().equals("stop")) throw new IOException("Jawaban terpotong");
                    return content.toString().trim();
                }
            }
            throw new IOException("Stream belum selesai");
        }
        var root = json.readTree(raw);
        var choice = root == null ? json.missingNode() : root.path("choices").path(0);
        if (!choice.path("finish_reason").asText().equals("stop")) throw new IOException("Jawaban belum selesai");
        String result = choice.path("message").path("content").asText("").trim();
        if (result.isEmpty()) throw new IOException("Jawaban kosong");
        return result;
    }
}
