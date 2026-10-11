package id.indomonitor.media.assistant;

import id.indomonitor.media.api.QueryFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {
    private final AssistantService service;
    public AssistantController(AssistantService service) { this.service = service; }
    public record Turn(@NotBlank @Pattern(regexp="user|assistant") String role, @NotBlank @Size(max=6000) String content) {}
    public record Input(@NotBlank @Size(max=1200) String question, @NotNull Instant from, @NotNull Instant to,
        @Size(max=100) String q, @Size(max=200) String domain, String scope, boolean useWeb,
        @Size(max=6) List<@NotNull @Valid Turn> history) {}
    @GetMapping("/status") public Map<String, Object> status() { return service.status(); }
    @PostMapping public Map<String, Object> ask(@Valid @RequestBody Input input) {
        var filter = new QueryFilter(input.from(), input.to(), input.q(), input.domain(), input.scope());
        return service.ask(input.question().strip(), filter, input.useWeb(), input.history() == null ? List.of() : input.history());
    }
}
