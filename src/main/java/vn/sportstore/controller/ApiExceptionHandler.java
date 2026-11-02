package vn.sportstore.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> denied(AccessDeniedException error, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("timestamp", Instant.now().toString(),
                "status", 403, "message", "Bạn không có quyền thực hiện thao tác này",
                "path", request.getRequestURI()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> status(ResponseStatusException error, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(error.getStatusCode().value());
        return ResponseEntity.status(status).body(Map.of("timestamp", Instant.now().toString(),
                "status", status.value(), "message", error.getReason() == null ? "Yêu cầu không hợp lệ" : error.getReason(),
                "path", request.getRequestURI()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> integrity(DataIntegrityViolationException error, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("timestamp", Instant.now().toString(),
                "status", 409, "message", "Dữ liệu đang được tham chiếu hoặc vi phạm ràng buộc. Hãy kiểm tra lại các đơn hàng liên quan.",
                "path", request.getRequestURI()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> missing(NoResourceFoundException error, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("timestamp", Instant.now().toString(),
                "status", 404, "message", "Không tìm thấy đường dẫn yêu cầu", "path", request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unexpected(Exception error, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("timestamp", Instant.now().toString(),
                "status", 500, "message", "Đã xảy ra lỗi máy chủ. Vui lòng thử lại hoặc kiểm tra dữ liệu.",
                "path", request.getRequestURI()));
    }
}
