package com.ihh.wpBot.controller;

import com.ihh.wpBot.model.MediaRequest;
import com.ihh.wpBot.model.BulkMediaType;
import com.ihh.wpBot.model.ResumeSendRequest;
import com.ihh.wpBot.model.SendRequest;
import com.ihh.wpBot.model.SendSession;
import com.ihh.wpBot.model.SendStatus;
import com.ihh.wpBot.service.MessageSendingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/send")
@PreAuthorize("isAuthenticated()")
public class SendController {

    private static final Logger log = LoggerFactory.getLogger(SendController.class);

    private final MessageSendingService sendingService;

    @Autowired
    public SendController(MessageSendingService sendingService) {
        this.sendingService = sendingService;
    }

    @PostMapping("/start")
    public ResponseEntity<?> startSending(@RequestBody SendRequest request) {
        String resolvedTemplate = request.getResolvedTemplateName();
        if (resolvedTemplate == null || resolvedTemplate.isBlank()) {
            log.warn("Bulk send rejected: templateName missing. sessionId={}", request.getSessionId());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "TEMPLATE_NAME_REQUIRED",
                    "message", "Şablon adı belirtilmelidir. Lütfen toplu gönderim için bir hazır kayıt seçin."
            ));
        }
        if (request.getPhoneNumbers() == null || request.getPhoneNumbers().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "PHONE_NUMBERS_REQUIRED",
                    "message", "En az bir telefon numarası belirtilmelidir."
            ));
        }

        try {
            List<String> cleanedNumbers = request.getPhoneNumbers().stream()
                    .map(entry -> {
                        if (entry.contains(" - ")) {
                            return entry.substring(
                                    entry.lastIndexOf(" - ") + 3).trim();
                        }
                        return entry.trim();
                    })
                    .filter(n -> !n.isBlank())
                    .collect(Collectors.toList());

            SendSession session = sendingService.createSession(cleanedNumbers.size());

            List<String> mediaUrls = new ArrayList<>();
            String mediaFilename = request.getFilename();
            BulkMediaType mediaType = request.getMediaType();
            if (request.getMedia() != null && !request.getMedia().isEmpty()) {
                for (MediaRequest m : request.getMedia()) {
                    String safeUrl = m.getUrl()
                            .replace("94.130.231.165", "localhost");
                    mediaUrls.add(safeUrl);
                    if ((mediaFilename == null || mediaFilename.isBlank()) && m.getFileName() != null && !m.getFileName().isBlank()) {
                        mediaFilename = m.getFileName();
                    }
                    if (mediaType == null) {
                        mediaType = mapMediaType(m.getType());
                    }
                }
            }
            if (mediaUrls.isEmpty()) {
                String resolvedMediaUrl = request.getResolvedMediaUrl();
                if (resolvedMediaUrl != null && !resolvedMediaUrl.isBlank()) {
                    mediaUrls.add(resolvedMediaUrl);
                }
            }

            List<String> personalizedMessages =
                    request.getPersonalizedMessages() != null
                            ? request.getPersonalizedMessages()
                            : List.of();

            int minDelay = request.getMinDelay() != null ? request.getMinDelay() : 0;
            int maxDelay = request.getMaxDelay() != null ? request.getMaxDelay() : 0;

            log.info("Bulk send started: template={}, phones={}, sessionId={}",
                    resolvedTemplate, cleanedNumbers.size(), request.getSessionId());

            sendingService.startSendingProcess(
                    session.getSessionId(),
                    request.getSessionId(),
                    cleanedNumbers,
                    resolvedTemplate,
                    request.getResolvedLanguage(),
                    minDelay,
                    maxDelay,
                    mediaUrls,
                    mediaType,
                    mediaFilename,
                    personalizedMessages,
                    request.isPersonalized()  // Lombok bunu otomatik üretiyor ✅
            );

            return ResponseEntity.ok(Map.of(
                    "sessionId",    session.getSessionId(),
                    "totalNumbers", cleanedNumbers.size(),
                    "status",       session.getStatus().toString()
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body("Hatalı istek: " + e.getMessage());
        }
    }

    @GetMapping("/status/{sessionId}")
    public ResponseEntity<SendSession> getStatus(@PathVariable String sessionId) {
        return ResponseEntity.ok(sendingService.getSession(sessionId));
    }

    @PostMapping("/stop/{sessionId}")
    public ResponseEntity<?> stopSending(@PathVariable String sessionId) {
        sendingService.stopSession(sessionId);
        SendSession session = sendingService.getSession(sessionId);
        return ResponseEntity.ok(Map.of(
                "sessionId", session.getSessionId(),
                "status",    session.getStatus().toString()
        ));
    }

    @PostMapping("/resume/{sessionId}")
    public ResponseEntity<?> resumeSending(
            @PathVariable String sessionId,
            @RequestBody ResumeSendRequest request) {
        try {
            SendSession session = sendingService.getActiveSession(sessionId);
            if (session == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Session bellekte bulunamadı. Uygulama yeniden başladıysa oturum temizlenmiş olabilir."
                ));
            }

            if (session.getStatus() != SendStatus.RATE_LIMITED) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Resume sadece RATE_LIMITED durumundaki session için kullanılabilir.",
                        "currentStatus", session.getStatus().toString()
                ));
            }

            List<String> cleanedNumbers = request.getPhoneNumbers().stream()
                    .map(entry -> {
                        if (entry.contains(" - ")) {
                            return entry.substring(entry.lastIndexOf(" - ") + 3).trim();
                        }
                        return entry.trim();
                    })
                    .filter(n -> !n.isBlank())
                    .collect(Collectors.toList());

            List<String> mediaPaths = request.getMediaPaths() != null
                    ? request.getMediaPaths().stream()
                            .map(url -> url.replace("94.130.231.165", "localhost"))
                            .collect(Collectors.toList())
                    : List.of();

            sendingService.resumeSession(
                    sessionId,
                    request.getWhatsappSessionId(),
                    cleanedNumbers,
                    request.getTemplateName(),
                    mediaPaths,
                    BulkMediaType.IMAGE,
                    null,
                    request.isPersonalized()
            );

            SendSession updated = sendingService.getSession(sessionId);
            return ResponseEntity.ok(Map.of(
                    "sessionId", updated.getSessionId(),
                    "status", updated.getStatus().toString(),
                    "sentCount", updated.getSentCount(),
                    "totalNumbers", updated.getTotalNumbers(),
                    "message", "Session kaldığı yerden devam ettirildi."
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private BulkMediaType mapMediaType(String mediaTypeRaw) {
        if (mediaTypeRaw == null || mediaTypeRaw.isBlank()) {
            return BulkMediaType.IMAGE;
        }
        String normalized = mediaTypeRaw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "VIDEO" -> BulkMediaType.VIDEO;
            case "DOCUMENT", "DOC", "FILE" -> BulkMediaType.DOCUMENT;
            default -> BulkMediaType.IMAGE;
        };
    }
}
