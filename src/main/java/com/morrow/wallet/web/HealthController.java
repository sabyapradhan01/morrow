package com.morrow.wallet.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/health")
    public Map<String, String> applicationHealth() {
        return Map.of("application", "Morrow Wallet", "status", "ok");
    }

    @GetMapping("/health/db")
    public ResponseEntity<Map<String, String>> databaseHealth() {
        try {
            String result = jdbcTemplate.queryForObject("SELECT 'connected'", String.class);
            Map<String, String> body = new LinkedHashMap<>();
            body.put("application", "Morrow Wallet");
            body.put("database", "PostgreSQL (Neon)");
            body.put("connectivity", "PostgreSQL JDBC Driver");
            body.put("status", "ok");
            body.put("check", result == null ? "query returned no value" : result);
            return ResponseEntity.ok(body);
        } catch (DataAccessException ex) {
            // Deliberately avoid returning credentials or driver details to the browser.
            Map<String, String> body = new LinkedHashMap<>();
            body.put("application", "Morrow Wallet");
            body.put("database", "PostgreSQL (Neon)");
            body.put("connectivity", "PostgreSQL JDBC Driver");
            body.put("status", "unavailable");
            body.put("message", "Check NEON_JDBC_URL, NEON_DB_USER and NEON_DB_PASSWORD in the hosting environment, and verify the Neon database is available.");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }
    }
}
