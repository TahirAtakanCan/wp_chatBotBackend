package com.ihh.wpBot.controller;

import com.ihh.wpBot.model.SendSession;
import com.ihh.wpBot.service.MessageSendingService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/send")
public class SendController {

    private final MessageSendingService sendingService;

    @Autowired
    public SendController(MessageSendingService sendingService) {
        this.sendingService = sendingService;
    }

    @PostMapping("/start")
    public ResponseEntity<?> startSending(
            @RequestParam("phoneNumbers") String phoneNumbersJson,
            @RequestParam("message") String message,
            @RequestParam("minDelay") int minDelay,
            @RequestParam("maxDelay") int maxDelay,
            @RequestParam(value = "media", required = false) MultipartFile[] media) {

        try {
            // Flutter'dan gelen JSON metnini Java Listesine (Array) çevir
            ObjectMapper mapper = new ObjectMapper();
            List<String> phoneNumbers = mapper.readValue(phoneNumbersJson, new TypeReference<List<String>>() {});

            SendSession session = sendingService.createSession(phoneNumbers.size());
            
            // İşlemi arka planda başlat ve cevabı hemen dön (Uygulama kilitlenmesin diye)
            sendingService.startSendingProcess(session.getSessionId(), phoneNumbers, message, minDelay, maxDelay, media);

            return ResponseEntity.ok(Map.of(
                    "sessionId", session.getSessionId(),
                    "totalNumbers", phoneNumbers.size(),
                    "status", session.getStatus().toString()
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Hatalı istek: " + e.getMessage());
        }
    }

    @GetMapping("/status/{sessionId}")
    public ResponseEntity<SendSession> getStatus(@PathVariable String sessionId) {
        // Flutter bu adrese 2 saniyede bir istek atıp logları okuyacak
        return ResponseEntity.ok(sendingService.getSession(sessionId));
    }

    @PostMapping("/stop/{sessionId}")
    public ResponseEntity<?> stopSending(@PathVariable String sessionId) {
        sendingService.stopSession(sessionId);
        SendSession session = sendingService.getSession(sessionId);
        
        return ResponseEntity.ok(Map.of(
                "sessionId", session.getSessionId(),
                "status", session.getStatus().toString(),
                "sentCount", session.getSentCount(),
                "totalNumbers", session.getTotalNumbers()
        ));
    }
}