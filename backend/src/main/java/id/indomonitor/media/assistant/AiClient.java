package id.indomonitor.media.assistant;

import com.fasterxml.jackson.databind.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
public class AiClient {
    private final GatewayClient gateway;
    private final ObjectMapper json;
    private final String url, key, model;
    public AiClient(GatewayClient gateway, ObjectMapper json, @Value("${media.ai-url}") String url,
        @Value("${media.ai-key}") String key, @Value("${media.ai-model}") String model) {
        this.gateway = gateway; this.json = json; this.url = url; this.key = key; this.model = model;
    }
    public boolean configured() { return !key.isBlank() && !model.isBlank() && !url.isBlank(); }
    public String model() { return model; }
    public JsonNode answer(String instruction, Object evidence) {
        if (!configured()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Konfigurasi AI_API_KEY dan AI_MODEL belum lengkap.");
        try {
            var body = Map.of("model", model, "stream", false, "temperature", 0, "max_tokens", 2400,
                "messages", List.of(Map.of("role", "system", "content", instruction),
                    Map.of("role", "user", "content", json.writeValueAsString(evidence))));
            String content = GatewayClient.completion(gateway.post(url, "/chat/completions", key, body, 75), json);
            if (content.startsWith("```")) content = content.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
            JsonNode result = json.readTree(content);
            if (result == null || !result.isObject()) throw new IllegalArgumentException();
            return result;
        } catch (ResponseStatusException ex) { throw ex; }
        catch (Exception ex) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Jawaban AI tidak valid. Tidak ada jawaban buatan pengganti."); }
    }
}
