package com.ihh.wpBot.controller;

import com.ihh.wpBot.config.JwtUtil;
import com.ihh.wpBot.model.SessionRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SessionController {

    private final JwtUtil jwtUtil;

    public SessionController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/session/create")
    public ResponseEntity<Map<String, Object>> createSession(@RequestBody SessionRequest request) {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Meta API kullanılıyor. Harici session kurulumu artık gereksizdir.");
        result.put("status", "SUCCESS");
        return ResponseEntity.ok(result);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<Map<String, Object>> deleteSession(@PathVariable String sessionId) {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Meta API kullanılıyor. Oturum silme işlemine gerek duyulmaz.");
        result.put("status", "DELETED");
        return ResponseEntity.ok(result);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/session/{sessionId}/status")
    public ResponseEntity<Map<String, Object>> getSessionStatus(
            @PathVariable String sessionId,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            String role = jwtUtil.extractRole(token);
            String userSessionId = jwtUtil.extractSessionId(token);

            // USER rolü sadece kendi session'ını görebilir
            if ("USER".equals(role) && (userSessionId == null || !userSessionId.equals(sessionId))) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Bu session'a erişim yetkiniz yok");
                return ResponseEntity.status(403).body(error);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("status", "CONNECTED");
            result.put("message", "Meta API ile bağlı.");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Session durumu alınamadı: " + e.getMessage());
            return ResponseEntity.status(503).body(error);
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/sessions")
    public ResponseEntity<Map<String, Object>> getAllSessions() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Meta API üzerinde çalışılmaktadır, bağımsız oturumlar bulunmamaktadır.");
        return ResponseEntity.ok(result);
    }
}
