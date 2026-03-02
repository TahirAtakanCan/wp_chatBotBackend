package com.ihh.wpBot.controller;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
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
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/media")
@CrossOrigin(origins = "*") // Flutter'dan erişim için
public class MediaController {
    
    // Resimlerin bilgisayarda kaydedileceği klasör
    private final String UPLOAD_DIR = "uploads/";

    public MediaController() {
        File dir = new File(UPLOAD_DIR);
        if (!dir.exists()) {
            dir.mkdirs(); // Klasör yoksa oluştur
        }
    }

    // 1. Sunucuya Resim Yükleme (Admin için)
    @PostMapping("/upload")
    public ResponseEntity<String> uploadMedia(@RequestParam("file") MultipartFile file) {
        try {
            Path path = Paths.get(UPLOAD_DIR + file.getOriginalFilename());
            Files.write(path, file.getBytes());
            return ResponseEntity.ok("Başarıyla yüklendi: " + file.getOriginalFilename());
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Yükleme hatası: " + e.getMessage());
        }
    }

    // 2. Yüklü Resimlerin Listesini Getirme (Flutter Medya Seçici için)
    @GetMapping("/list")
    public ResponseEntity<List<String>> listMedia() {
        try {
            List<String> files = Files.walk(Paths.get(UPLOAD_DIR))
                    .filter(Files::isRegularFile)
                    // Her dosya için dışarıdan erişilebilecek URL oluşturuyoruz
                    .map(path -> "http://localhost:8080/api/media/" + path.getFileName().toString())
                    .collect(Collectors.toList());
            return ResponseEntity.ok(files);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // 3. Resmi URL Üzerinden Dışarı Sunma (WhatsApp'ın resmi çekeceği yer)
    @GetMapping("/{filename}")
    public ResponseEntity<Resource> getMedia(@PathVariable String filename) {
        try {
            Path file = Paths.get(UPLOAD_DIR).resolve(filename);
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() || resource.isReadable()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG) // Önizleme için formatı belirtiyoruz
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
