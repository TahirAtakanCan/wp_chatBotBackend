package com.ihh.wpBot.controller;

import com.ihh.wpBot.model.SessionRequest;
import com.ihh.wpBot.service.WhatsAppService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SessionController {

    private final WhatsAppService whatsAppService;

    public SessionController(WhatsAppService whatsAppService) {
        this.whatsAppService = whatsAppService;
    }

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

    @GetMapping("/session/{sessionId}/status")
    public ResponseEntity<Map<String, Object>> getSessionStatus(@PathVariable String sessionId) {
        try {
            Map<String, Object> result = whatsAppService.getSessionStatus(sessionId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Session durumu alınamadı: " + e.getMessage());
            return ResponseEntity.status(503).body(error);
        }
    }

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
