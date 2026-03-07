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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/media")
@CrossOrigin(origins = "*") // Flutter'dan erişim için
public class MediaController {

    private static final String SERVER_BASE_URL = "http://94.130.231.165:8080";

    // Resimlerin bilgisayarda kaydedileceği klasör
    private final String UPLOAD_DIR = "uploads/";

    public MediaController() {
        File dir = new File(UPLOAD_DIR);
        if (!dir.exists()) {
            dir.mkdirs(); // Klasör yoksa oluştur
        }
    }

    // 1. Sunucuya Resim Yükleme
    @PostMapping("/upload")
    public ResponseEntity<?> uploadMedia(@RequestParam("file") MultipartFile file) {
        try {
            String originalFilename = file.getOriginalFilename();
            
            // ÇOK ÖNEMLİ: Boşlukları ve Türkçe karakterleri temizle, URL'in bozulmasını engelle
            String sanitizedFilename = originalFilename != null ? originalFilename.replaceAll("[^a-zA-Z0-9\\.\\-]", "_") : "resim.jpg";
            String safeFilename = System.currentTimeMillis() + "_" + sanitizedFilename;

            Path targetPath = Paths.get(UPLOAD_DIR).resolve(safeFilename);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            // DİKKAT: /uploads/ yerine zaten aşağıda tanımlı olan /api/media/ metoduna yönlendiriyoruz
            String fileUrl = SERVER_BASE_URL + "/api/media/" + safeFilename;

            return ResponseEntity.ok(Map.of("url", fileUrl));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Yükleme hatası: " + e.getMessage()));
        }
    }

    // 2. Yüklü Resimlerin Listesini Getirme (Flutter Medya Seçici için)
    @GetMapping("/list")
    public ResponseEntity<List<String>> listMedia() {
        try {
            List<String> files = Files.walk(Paths.get(UPLOAD_DIR))
                    .filter(Files::isRegularFile)
                    // Her dosya için dışarıdan erişilebilecek URL oluşturuyoruz
                    .map(path -> SERVER_BASE_URL + "/uploads/" + path.getFileName().toString())
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
