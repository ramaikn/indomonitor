package id.indomonitor.media.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value=AssistantController.class, properties="media.admin-key=01234567890123456789012345678901")
class AssistantControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AssistantService service;
    private final String input = "{\"question\":\"Tren apa?\",\"from\":\"2026-10-08T00:00:00Z\",\"to\":\"2026-10-09T00:00:00Z\",\"scope\":\"indonesian_outlet\",\"useWeb\":false}";
    @Test void requiresTrustedOriginAndJsonWithoutAdminKey() throws Exception {
        mvc.perform(post("/api/assistant").contentType("application/json").content(input)).andExpect(status().isForbidden());
        mvc.perform(post("/api/assistant").header("Origin", "https://evil.example").contentType("application/json").content(input)).andExpect(status().isForbidden());
        mvc.perform(post("/api/assistant").header("Origin", "http://127.0.0.1:5173").contentType("text/plain").content(input)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
        when(service.ask(anyString(), any(), anyBoolean(), anyList())).thenReturn(Map.of("status", "answered"));
        mvc.perform(post("/api/assistant").header("Origin", "http://127.0.0.1:5173").contentType("application/json").content(input))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("answered"));
    }
    @Test void rejectsInvalidDatesQuestionAndHistory() throws Exception {
        for (String bad : java.util.List.of(input.replace("Tren apa?", ""), input.replace("2026-10-08", "2026-10-10"),
            input.replace("\"useWeb\":false", "\"history\":[null]"), input.replace("indonesian_outlet", "invalid"))) {
            mvc.perform(post("/api/assistant").header("Origin", "http://127.0.0.1:5173").contentType("application/json").content(bad))
                .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
}
