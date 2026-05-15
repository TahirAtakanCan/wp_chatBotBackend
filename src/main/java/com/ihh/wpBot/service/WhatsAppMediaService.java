package com.ihh.wpBot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Optional;

@Service
public class WhatsAppMediaService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppMediaService.class);

    private static final String MEDIA_API_BASE = "https://graph.facebook.com/v19.0/";
    private static final Path INBOUND_MEDIA_DIR = Paths.get("uploads", "inbound");

    private final RestTemplate restTemplate;
    private final String accessToken;

    public WhatsAppMediaService(RestTemplate restTemplate, @Value("${meta.access.token}") String accessToken) {
        this.restTemplate = restTemplate;
        this.accessToken = accessToken;
    }

    public Optional<StoredMedia> downloadIncomingMedia(String mediaId) {
        if (mediaId == null || mediaId.isBlank()) {
            return Optional.empty();
        }
        try {
            Map<String, Object> metadata = fetchMediaMetadata(mediaId);
            String downloadUrl = asNonBlankString(metadata.get("url"));
            if (downloadUrl == null) {
                log.warn("Meta media metadata has no url. mediaId={}", mediaId);
                return Optional.empty();
            }

            String mimeType = asNonBlankString(metadata.get("mime_type"));
            byte[] bytes = fetchMediaBytes(downloadUrl);
            if (bytes == null || bytes.length == 0) {
                log.warn("Meta media bytes empty. mediaId={}", mediaId);
                return Optional.empty();
            }

            Files.createDirectories(INBOUND_MEDIA_DIR);
            String extension = extensionForMimeType(mimeType);
            String safeMediaId = sanitizeMediaId(mediaId);
            Path target = INBOUND_MEDIA_DIR.resolve(safeMediaId + extension);
            Files.write(target, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            return Optional.of(new StoredMedia(target.toString(), mimeType));
        } catch (Exception e) {
            log.error("Failed to download incoming media from Meta. mediaId={}", mediaId, e);
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchMediaMetadata(String mediaId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                MEDIA_API_BASE + mediaId,
                HttpMethod.GET,
                request,
                Map.class
        );
        return response.getBody();
    }

    private byte[] fetchMediaBytes(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<byte[]> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                byte[].class
        );
        return response.getBody();
    }

    private String extensionForMimeType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return ".bin";
        }
        String normalized = mimeType.toLowerCase();
        if ("image/jpeg".equals(normalized) || "image/jpg".equals(normalized)) {
            return ".jpg";
        }
        if ("image/png".equals(normalized)) {
            return ".png";
        }
        if ("image/webp".equals(normalized)) {
            return ".webp";
        }
        if ("video/mp4".equals(normalized)) {
            return ".mp4";
        }
        if ("audio/ogg".equals(normalized)) {
            return ".ogg";
        }
        if ("audio/mpeg".equals(normalized)) {
            return ".mp3";
        }
        int slashIdx = normalized.indexOf('/');
        if (slashIdx >= 0 && slashIdx + 1 < normalized.length()) {
            String subtype = normalized.substring(slashIdx + 1).trim();
            if (!subtype.isBlank() && subtype.length() <= 10 && subtype.matches("[a-z0-9.+-]+")) {
                return "." + subtype.replace("+xml", "");
            }
        }
        return ".bin";
    }

    private String sanitizeMediaId(String mediaId) {
        return mediaId.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String asNonBlankString(Object value) {
        if (!(value instanceof String str)) {
            return null;
        }
        String trimmed = str.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    public record StoredMedia(String storagePath, String mimeType) {
    }
}
