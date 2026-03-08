package com.ihh.wpBot.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/media")
@CrossOrigin(origins = "*") 
public class MediaController {

    @Value("${app.server.url}")
    private String serverBaseUrl;

    private final String UPLOAD_DIR = "uploads/";

    public MediaController() {
        File dir = new File(UPLOAD_DIR);
        if (!dir.exists()) {
            dir.mkdirs(); 
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadMedia(@RequestParam("file") MultipartFile file) {
        try {
            // 1. Dosya adındaki Türkçe karakterleri ve boşlukları temizle
            String originalFilename = file.getOriginalFilename();
            String sanitized = "resim.jpg";
            if (originalFilename != null) {
                // Sadece harf, rakam, nokta ve alt tireye izin ver
                sanitized = originalFilename.replaceAll("[^a-zA-Z0-9\\.\\-]", "_");
            }
            
            String safeFilename = System.currentTimeMillis() + "_" + sanitized;

            Path targetPath = Paths.get(UPLOAD_DIR).resolve(safeFilename);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            // 2. DİKKAT: URL formatı /api/media/{dosyaismi} olmalı, araya /uploads/ girmemeli!
            String fileUrl = serverBaseUrl + "/api/media/" + safeFilename;

            return ResponseEntity.ok(Collections.singletonMap("url", fileUrl));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", "Hata: " + e.getMessage()));
        }
    }

    @GetMapping("/list")
    public ResponseEntity<List<String>> listMedia() {
        try {
            List<String> files = Files.walk(Paths.get(UPLOAD_DIR))
                    .filter(Files::isRegularFile)
                    .map(path -> serverBaseUrl + "/api/media/" + path.getFileName().toString())
                    .collect(Collectors.toList());
            return ResponseEntity.ok(files);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{filename}")
    public ResponseEntity<Resource> getMedia(@PathVariable String filename) {
        try {
            Path file = Paths.get(UPLOAD_DIR).resolve(filename);
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() || resource.isReadable()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG) 
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // ─── Base64 resmi Node.js'e proxy'le ───
    @PostMapping("/send")
    public ResponseEntity<?> sendMediaToNode(@RequestBody Map<String, String> payload) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, String>> request = new HttpEntity<>(payload, headers);

            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.postForEntity(
                    "http://localhost:3000/send-media", request, String.class);

            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Collections.singletonMap("error",
                            "Node.js servisine bağlanılamadı: " + e.getMessage()));
        }
    }
}
