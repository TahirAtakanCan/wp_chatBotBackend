package com.ihh.wpBot.controller;

import com.ihh.wpBot.config.JwtUtil;
import com.ihh.wpBot.model.SessionRequest;
import com.ihh.wpBot.service.WhatsAppService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SessionController {

    private final WhatsAppService whatsAppService;
    private final JwtUtil jwtUtil;

    public SessionController(WhatsAppService whatsAppService, JwtUtil jwtUtil) {
        this.whatsAppService = whatsAppService;
        this.jwtUtil = jwtUtil;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/session/create")
    public ResponseEntity<Map<String, Object>> createSession(@RequestBody SessionRequest request) {
        try {
            Map<String, Object> result = whatsAppService.createSession(request.getSessionId());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Session oluşturulamadı: " + e.getMessage());
            return ResponseEntity.status(503).body(error);
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<Map<String, Object>> deleteSession(@PathVariable String sessionId) {
        try {
            Map<String, Object> result = whatsAppService.deleteSession(sessionId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Session silinemedi: " + e.getMessage());
            return ResponseEntity.status(503).body(error);
        }
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

            Map<String, Object> result = whatsAppService.getSessionStatus(sessionId);
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
        try {
            Map<String, Object> result = whatsAppService.getAllSessions();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Session listesi alınamadı: " + e.getMessage());
            return ResponseEntity.status(503).body(error);
        }
    }
}
