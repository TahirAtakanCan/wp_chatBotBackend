package com.ihh.wpBot.controller;

import com.ihh.wpBot.config.JwtUtil;
import com.ihh.wpBot.model.MessageTemplate;
import com.ihh.wpBot.service.MessageTemplateService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/templates")
@PreAuthorize("isAuthenticated()")
public class MessageTemplateController {

    private final MessageTemplateService messageTemplateService;
    private final JwtUtil jwtUtil;

    public MessageTemplateController(MessageTemplateService messageTemplateService, JwtUtil jwtUtil) {
        this.messageTemplateService = messageTemplateService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping
    public ResponseEntity<?> getTemplates(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            String username = jwtUtil.extractUsername(token);
            String role = jwtUtil.extractRole(token);

            List<MessageTemplate> templates;
            if ("ADMIN".equals(role)) {
                templates = messageTemplateService.getAllTemplates();
            } else {
                templates = messageTemplateService.getTemplatesByUser(username);
            }

            return ResponseEntity.ok(templates);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Sablonlar alinamadi: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping
    public ResponseEntity<?> createTemplate(@RequestBody Map<String, String> request,
                                            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            String createdBy = jwtUtil.extractUsername(token);

            MessageTemplate created = messageTemplateService.createTemplate(
                    request.get("title"),
                    request.get("content"),
                    createdBy
            );

            return ResponseEntity.ok(created);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Sablon olusturulamadi: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTemplate(@PathVariable Long id,
                                            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            String username = jwtUtil.extractUsername(token);
            String role = jwtUtil.extractRole(token);

            messageTemplateService.deleteTemplate(id, username, role);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Sablon silindi");
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(403).body(error);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Sablon silinemedi: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateTemplate(@PathVariable Long id,
                                            @RequestBody Map<String, String> request,
                                            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            String username = jwtUtil.extractUsername(token);
            String role = jwtUtil.extractRole(token);

            if ("USER".equals(role)) {
                MessageTemplate existingTemplate = messageTemplateService.getTemplateById(id);
                if (!username.equals(existingTemplate.getCreatedBy())) {
                    Map<String, String> error = new HashMap<>();
                    error.put("error", "Bu sablonu guncelleme yetkiniz yok");
                    return ResponseEntity.status(403).body(error);
                }
            }

            MessageTemplate updated = messageTemplateService.updateTemplate(
                    id,
                    request.get("title"),
                    request.get("content")
            );

            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Sablon guncellenemedi: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}
