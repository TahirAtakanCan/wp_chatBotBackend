package com.ihh.wpBot.controller;

import com.ihh.wpBot.model.Role;
import com.ihh.wpBot.model.User;
import com.ihh.wpBot.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllUsers() {
        List<Map<String, Object>> users = userService.getAllUsers().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody Map<String, String> request) {
        try {
            String username = request.get("username");
            String password = request.get("password");
            Role role = Role.valueOf(request.getOrDefault("role", "USER"));
            String sessionId = request.get("sessionId");

            User user = userService.createUser(username, password, role, sessionId);

            if (sessionId != null && !sessionId.isBlank()) {
                // Meta API kullanıldığı için Node.js üzerinden WhatsApp session oluşturmaya gerek yok
                System.out.println("Meta API modu devrede, yeni oluşturulan User için harici session tetiklenmedi.");
            }

            return ResponseEntity.ok(toResponse(user));
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Kullanıcı oluşturulamadı: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        try {
            userService.deleteUser(id);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Kullanıcı silindi");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Kullanıcı silinemedi: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PutMapping("/{id}/session")
    public ResponseEntity<?> updateSessionId(@PathVariable Long id,
                                              @RequestBody Map<String, String> request) {
        try {
            String sessionId = request.get("sessionId");
            User user = userService.updateSessionId(id, sessionId);
            return ResponseEntity.ok(toResponse(user));
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Session güncellenemedi: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    private Map<String, Object> toResponse(User user) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("role", user.getRole().name());
        map.put("sessionId", user.getSessionId());
        map.put("createdAt", user.getCreatedAt().toString());
        return map;
    }
}
