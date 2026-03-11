package com.ihh.wpBot.controller;

import com.ihh.wpBot.model.Contact;
import com.ihh.wpBot.service.ContactService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/contacts")
public class ContactController {
    @Autowired
    private ContactService contactService;

    private String getUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }

    private String getRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getAuthorities().stream().findFirst().map(a -> a.getAuthority().replace("ROLE_", "")).orElse("");
    }

    @GetMapping
    public ResponseEntity<List<Contact>> getAllContacts() {
        String username = getUsername();
        String role = getRole();
        return ResponseEntity.ok(contactService.getAllContacts(username, role));
    }

    @PostMapping(value = "/import", consumes = {
            MediaType.APPLICATION_JSON_VALUE,
            MediaType.MULTIPART_FORM_DATA_VALUE
    })
    public ResponseEntity<Map<String, Integer>> importContacts(
            @RequestBody(required = false) Map<String, String> body,
            @RequestParam(value = "file", required = false) MultipartFile file
    ) {
        String csvContent = "";
        String createdBy = getUsername();
        if (file != null && !file.isEmpty()) {
            try {
                csvContent = new String(file.getBytes(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                Map<String, Integer> errorResp = new HashMap<>();
                errorResp.put("imported", 0);
                errorResp.put("skipped", 0);
                return ResponseEntity.status(400).body(errorResp);
            }
        } else if (body != null && body.containsKey("csvContent")) {
            csvContent = body.getOrDefault("csvContent", "");
        }
        ContactService.ImportResult result = contactService.importFromCsv(csvContent, createdBy);
        Map<String, Integer> resp = new HashMap<>();
        resp.put("imported", result.imported);
        resp.put("skipped", result.skipped);
        return ResponseEntity.ok(resp);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteContact(@PathVariable Long id) {
        String username = getUsername();
        String role = getRole();
        boolean deleted = contactService.deleteContact(id, username, role);
        if (deleted) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.status(403).body("Unauthorized");
        }
    }

    @GetMapping("/search")
    public ResponseEntity<List<Contact>> searchContacts(@RequestParam("q") String query) {
        String username = getUsername();
        String role = getRole();
        return ResponseEntity.ok(contactService.searchContacts(query, username, role));
    }
}
