package id.indomonitor.media.api;

import id.indomonitor.media.analysis.ReportService;
import id.indomonitor.media.analysis.SimilarityService;
import id.indomonitor.media.data.NewsRepository;
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

@WebMvcTest(value=NewsController.class, properties="media.admin-key=01234567890123456789012345678901")
class NewsControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean NewsRepository repository;
    @MockitoBean SimilarityService similarity;
    @MockitoBean ReportService reports;

    @Test void rejectsInvalidFilters() throws Exception {
        mvc.perform(get("/api/articles").param("size","101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/analytics").param("from","broken")).andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @Test void blocksUnauthenticatedWrite() throws Exception {
        mvc.perform(post("/api/watchlists").contentType("application/json")
            .content("{\"name\":\"Test\",\"keyword\":\"kereta\",\"threshold\":10}"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(repository);
    }

    @Test void validatesAndPersistsAuthorizedWatchlist() throws Exception {
        when(repository.createWatchlist(anyString(),anyString(),anyInt())).thenReturn(Map.of("id",1));
        mvc.perform(post("/api/watchlists").header("X-Admin-Key","01234567890123456789012345678901")
            .contentType("application/json").content("{\"name\":\"Test\",\"keyword\":\"kereta\",\"threshold\":10}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(1));
        mvc.perform(post("/api/watchlists").header("X-Admin-Key","01234567890123456789012345678901")
            .contentType("application/json").content("{\"name\":\"\",\"keyword\":\"\",\"threshold\":0}"))
            .andExpect(status().isBadRequest());
        verify(repository, times(1)).createWatchlist(anyString(),anyString(),anyInt());
    }
}
