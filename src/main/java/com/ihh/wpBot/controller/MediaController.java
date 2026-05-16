package com.ihh.wpBot.controller;

import com.ihh.wpBot.model.Message;
import com.ihh.wpBot.repository.MessageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
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

    @Value("${app.public.url}")
    private String publicUrl;

    private final MessageRepository messageRepository;

    private final String UPLOAD_DIR = "uploads/";

    public MediaController(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
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

            String fileUrl = normalizeBaseUrl(publicUrl) + "/api/media/public/" + safeFilename;
            long sizeBytes = file.getSize();
            String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

            return ResponseEntity.ok(Map.of(
                    "filename", safeFilename,
                    "url", fileUrl,
                    "type", contentType,
                    "size", sizeBytes,
                    "sizeFormatted", formatSize(sizeBytes)
            ));
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
        var storedMessageOpt = messageRepository.findTopByMediaIdOrderBySentAtDesc(filename);
        if (storedMessageOpt.isPresent()) {
            ResponseEntity<Resource> storedResponse = buildStoredMessageResponse(storedMessageOpt.get());
            if (storedResponse.getStatusCode().is2xxSuccessful()) {
                return storedResponse;
            }
        }

        try {
            Path file = Paths.get(UPLOAD_DIR).resolve(filename);
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() || resource.isReadable()) {
                String mimeType = Files.probeContentType(file);
                MediaType mediaType = resolveMediaType(mimeType);
                return ResponseEntity.ok()
                        .contentType(mediaType)
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/public/{filename}")
    public ResponseEntity<Resource> getPublicMedia(@PathVariable String filename) {
        try {
            Path file = Paths.get(UPLOAD_DIR).resolve(filename);
            Resource resource = new UrlResource(file.toUri());
            if (!(resource.exists() || resource.isReadable())) {
                return ResponseEntity.notFound().build();
            }
            String mimeType = Files.probeContentType(file);
            MediaType mediaType = resolveMediaType(mimeType);
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .body(resource);
        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
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

    private ResponseEntity<Resource> buildStoredMessageResponse(Message message) {
        if (message.getMediaStoragePath() == null || message.getMediaStoragePath().isBlank()) {
            return ResponseEntity.notFound().build();
        }
        Path path = Path.of(message.getMediaStoragePath());
        if (!Files.exists(path) || !Files.isReadable(path)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new PathResource(path);
        return ResponseEntity.ok()
                .contentType(resolveMediaType(message.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + path.getFileName() + "\"")
                .body(resource);
    }

    private MediaType resolveMediaType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(mimeType);
        } catch (Exception ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "";
        }
        String trimmed = baseUrl.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
