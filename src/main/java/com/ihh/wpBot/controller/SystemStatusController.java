package com.ihh.wpBot.controller;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@RestController
public class SystemStatusController {

    private static final String NODE_STATUS_URL = "http://localhost:3000/status";

    private final RestTemplate restTemplate;

    public SystemStatusController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @GetMapping("/system-status")
    public ResponseEntity<Map<String, Object>> getSystemStatus() {
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    NODE_STATUS_URL,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("qr", null);
            errorBody.put("connected", false);
            errorBody.put("user", null);
            errorBody.put("error", "Node.js servisine ulaşılamıyor");
            return ResponseEntity.status(503).body(errorBody);
        }
    }
}
