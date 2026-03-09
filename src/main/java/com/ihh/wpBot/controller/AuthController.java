package com.ihh.wpBot.controller;

import com.ihh.wpBot.config.JwtUtil;
import com.ihh.wpBot.model.Role;
import com.ihh.wpBot.model.User;
import com.ihh.wpBot.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final JwtUtil jwtUtil;

    public AuthController(AuthenticationManager authenticationManager,
                          UserService userService,
                          JwtUtil jwtUtil) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );

            UserDetails userDetails = userService.loadUserByUsername(username);
            User user = userService.findByUsername(username);

            String token = jwtUtil.generateToken(userDetails, user.getRole().name(), user.getSessionId());

            Map<String, Object> response = new HashMap<>();
            response.put("token", token);
            response.put("role", user.getRole().name());
            response.put("sessionId", user.getSessionId());
            response.put("username", user.getUsername());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Geçersiz kullanıcı adı veya şifre");
            return ResponseEntity.status(401).body(error);
        }
    }

    @PostMapping("/setup")
    public ResponseEntity<?> setup(@RequestBody Map<String, String> request) {
        if (userService.getUserCount() > 0) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Kurulum zaten tamamlanmış. Admin kullanıcı mevcut.");
            return ResponseEntity.status(403).body(error);
        }

        String username = request.get("username");
        String password = request.get("password");

        User admin = userService.createUser(username, password, Role.ADMIN, null);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Admin kullanıcı başarıyla oluşturuldu");
        response.put("username", admin.getUsername());
        response.put("role", admin.getRole().name());

        return ResponseEntity.ok(response);
    }
}
