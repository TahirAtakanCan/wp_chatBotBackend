package com.ihh.wpBot.controller;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/media")
@CrossOrigin(origins = "*") 
public class MediaController {

    private static final String SERVER_BASE_URL = "http://94.130.231.165:8080";
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
            String fileUrl = SERVER_BASE_URL + "/api/media/" + safeFilename;

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
                    .map(path -> SERVER_BASE_URL + "/api/media/" + path.getFileName().toString())
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
}
