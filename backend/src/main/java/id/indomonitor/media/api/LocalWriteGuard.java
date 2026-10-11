package id.indomonitor.media.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import java.net.URI;

@Component
public class LocalWriteGuard extends OncePerRequestFilter {
    private final byte[] key;
    private final Set<String> assistantOrigins;
    public LocalWriteGuard(@Value("${media.admin-key}") String key,
        @Value("${media.frontend-origin:http://127.0.0.1:5173}") String frontendOrigin) {
        if (key.length() < 32) throw new IllegalArgumentException("ADMIN_API_KEY minimal 32 karakter.");
        this.key = key.getBytes(StandardCharsets.UTF_8);
        this.assistantOrigins = Set.of(frontendOrigin);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws IOException, ServletException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "no-referrer");
        if (request.getRequestURI().equals("/api/assistant") && request.getMethod().equals("POST")) {
            String origin = request.getHeader("Origin");
            String contentType = request.getContentType();
            String host = request.getHeader("Host");
            boolean sameOrigin = false;
            try {
                URI parsed = origin == null ? null : URI.create(origin);
                sameOrigin = parsed != null && parsed.getScheme().equals(request.getScheme())
                    && parsed.getRawAuthority().equals(host) && parsed.getRawPath().isEmpty()
                    && parsed.getRawQuery() == null && parsed.getRawFragment() == null;
            } catch (Exception ignored) { }
            if (origin == null || (!assistantOrigins.contains(origin) && !sameOrigin)
                || contentType == null || !contentType.toLowerCase(java.util.Locale.ROOT).startsWith("application/json")) {
                response.setStatus(403); response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"Asisten hanya menerima JSON dari antarmuka yang diizinkan.\"}"); return;
            }
            chain.doFilter(request, response); return;
        }
        if (!Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod())) {
            String given = request.getHeader("X-Admin-Key");
            if (given == null || !MessageDigest.isEqual(key, given.getBytes(StandardCharsets.UTF_8))) {
                response.setStatus(401); response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"Kunci admin lokal diperlukan untuk perubahan.\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
