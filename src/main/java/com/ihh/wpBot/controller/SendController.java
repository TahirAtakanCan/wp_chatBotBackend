package com.ihh.wpBot.controller;

import com.ihh.wpBot.model.MediaRequest;
import com.ihh.wpBot.model.SendRequest;
import com.ihh.wpBot.model.SendSession;
import com.ihh.wpBot.service.MessageSendingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/send")
@PreAuthorize("isAuthenticated()")
public class SendController {

    private final MessageSendingService sendingService;

    @Autowired
    public SendController(MessageSendingService sendingService) {
        this.sendingService = sendingService;
    }

    @PostMapping("/start")
    public ResponseEntity<?> startSending(@RequestBody SendRequest request) {
        System.out.println("=== JAVA DEBUG ===");
        System.out.println("isPersonalized: " + request.isPersonalized());
        System.out.println("personalizedMessages: " + request.getPersonalizedMessages());
        System.out.println("phoneNumbers: " + request.getPhoneNumbers());
        System.out.println("message: " + request.getMessage());
        System.out.println("==================");

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
            if (request.getMedia() != null && !request.getMedia().isEmpty()) {
                for (MediaRequest m : request.getMedia()) {
                    String safeUrl = m.getUrl()
                            .replace("94.130.231.165", "localhost");
                    mediaUrls.add(safeUrl);
                }
            }

            List<String> personalizedMessages =
                    request.getPersonalizedMessages() != null
                            ? request.getPersonalizedMessages()
                            : List.of();

            sendingService.startSendingProcess(
                    session.getSessionId(),
                    request.getSessionId(),
                    cleanedNumbers,
                    request.getMessage(),
                    request.getMinDelay(),
                    request.getMaxDelay(),
                    mediaUrls,
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
}