package vn.sportstore.controller;

import java.sql.SQLException;
import java.util.Map;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private static final Logger logger = LoggerFactory.getLogger(HealthController.class);

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, String>> health() {
        try (var connection = dataSource.getConnection()) {
            if (connection.isValid(2)) {
                return ResponseEntity.ok(Map.of("status", "UP"));
            }
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "DOWN"));
        } catch (SQLException exception) {
            logger.warn("Database health check failed", exception);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "DOWN"));
        }
    }
}
