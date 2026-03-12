package com.ihh.wpBot.controller;

import com.ihh.wpBot.model.Contact;
import com.ihh.wpBot.service.ContactService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/contacts")
public class ContactController {

    @Autowired
    private com.ihh.wpBot.config.JwtUtil jwtUtil;

    @Autowired
    private ContactService contactService;

    @Autowired
    private RestTemplate restTemplate;

    private String getUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }

    private String getRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getAuthorities().stream().findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", "")).orElse("");
    }

    @GetMapping
    public ResponseEntity<List<Contact>> getAllContacts() {
        return ResponseEntity.ok(
                contactService.getAllContacts(getUsername(), getRole()));
    }

    @PostMapping("/import")
    public ResponseEntity<Map<String, Integer>> importContacts(
            @RequestBody Map<String, String> body) {
        String excelBase64 = body.getOrDefault("excelBase64", "");
        Map<String, Integer> errorResp = new HashMap<>();
        errorResp.put("imported", 0);
        errorResp.put("skipped", 0);
        if (excelBase64.isBlank()) {
            return ResponseEntity.badRequest().body(errorResp);
        }
        try {
            byte[] excelBytes = Base64.getDecoder().decode(excelBase64);
            ContactService.ImportResult result =
                    contactService.importFromExcel(excelBytes, getUsername());
            Map<String, Integer> resp = new HashMap<>();
            resp.put("imported", result.imported);
            resp.put("skipped", result.skipped);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(errorResp);
        }
    }

    @PostMapping("/sync-sheets")
    public ResponseEntity<Map<String, Integer>> syncFromSheets(
            @RequestBody Map<String, String> body) {
        String sheetUrl = body.getOrDefault("sheetUrl", "");
        Map<String, Integer> errorResp = new HashMap<>();
        errorResp.put("imported", 0);
        errorResp.put("skipped", 0);
        if (sheetUrl.isBlank()) {
            return ResponseEntity.badRequest().body(errorResp);
        }
        try {
            Pattern pattern = Pattern.compile("/spreadsheets/d/([a-zA-Z0-9_-]+)");
            Matcher matcher = pattern.matcher(sheetUrl);
            if (!matcher.find()) {
                return ResponseEntity.badRequest().body(errorResp);
            }
            String sheetId = matcher.group(1);
            String csvUrl = "https://docs.google.com/spreadsheets/d/"
                    + sheetId + "/gviz/tq?tqx=out:csv&sheet=Sayfa1";
            String csvContent = restTemplate.getForObject(csvUrl, String.class);
            if (csvContent == null || csvContent.isBlank()) {
                return ResponseEntity.status(502).body(errorResp);
            }
            ContactService.ImportResult result =
                    contactService.importFromGoogleSheets(csvContent, getUsername());
            Map<String, Integer> resp = new HashMap<>();
            resp.put("imported", result.imported);
            resp.put("skipped", result.skipped);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(errorResp);
        }
    }

    @DeleteMapping("/all")
    public ResponseEntity<?> deleteAll(
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        String username = jwtUtil.extractUsername(token);
        String role = jwtUtil.extractRole(token);
        contactService.deleteAllByUser(username, role);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteContact(@PathVariable Long id) {
        boolean deleted =
                contactService.deleteContact(id, getUsername(), getRole());
        return deleted
                ? ResponseEntity.ok().build()
                : ResponseEntity.status(403).body("Unauthorized");
    }

    @GetMapping("/search")
    public ResponseEntity<List<Contact>> searchContacts(
            @RequestParam("q") String query) {
        return ResponseEntity.ok(
                contactService.searchContacts(query, getUsername(), getRole()));
    }
}