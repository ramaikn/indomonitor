package id.indomonitor.media.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class ApiErrorHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);

    @ExceptionHandler({IllegalArgumentException.class, DateTimeParseException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<Map<String, String>> invalid(Exception exception) {
        return ResponseEntity.badRequest().body(Map.of("message", "Parameter tidak valid. Periksa tanggal, filter, dan ukuran halaman."));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> database(DataAccessException exception) {
        String id = UUID.randomUUID().toString();
        log.error("Database gagal, reference={}", id, exception);
        return ResponseEntity.status(503).body(Map.of("message", "Database tidak tersedia. Data tidak digantikan dengan angka nol.", "reference", id));
    }
}
