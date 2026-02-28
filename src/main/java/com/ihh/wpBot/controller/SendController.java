package com.ihh.wpBot.controller;

import com.ihh.wpBot.model.SendSession;
import com.ihh.wpBot.service.MessageSendingService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
            @RequestParam(value = "message", required = false) String message,
            @RequestParam("minDelay") int minDelay,
            @RequestParam("maxDelay") int maxDelay,
            @RequestParam(value = "media", required = false) MultipartFile[] media) {

        try {
            ObjectMapper mapper = new ObjectMapper();
            List<String> phoneNumbers = mapper.readValue(phoneNumbersJson, new TypeReference<List<String>>() {});

            // 1. MEDYA DOSYALARINI GEÇİCİ KLASÖRE KAYDET
            List<String> savedMediaPaths = new ArrayList<>();
            if (media != null && media.length > 0) {
                String tempDir = System.getProperty("java.io.tmpdir"); // Bilgisayarın Temp klasörü
                for (MultipartFile file : media) {
                    // İsim çakışmasını önlemek için benzersiz bir isim oluştur
                    String uniqueFileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
                    File tempFile = new File(tempDir, uniqueFileName);
                    file.transferTo(tempFile);
                    savedMediaPaths.add(tempFile.getAbsolutePath());
                }
            }

            SendSession session = sendingService.createSession(phoneNumbers.size());
            
            // 2. KAYDEDİLEN DOSYA YOLLARINI SERVİSE GÖNDER
            sendingService.startSendingProcess(session.getSessionId(), phoneNumbers, message, minDelay, maxDelay, savedMediaPaths);

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
        return ResponseEntity.ok(sendingService.getSession(sessionId));
    }

    @PostMapping("/stop/{sessionId}")
    public ResponseEntity<?> stopSending(@PathVariable String sessionId) {
        sendingService.stopSession(sessionId);
        SendSession session = sendingService.getSession(sessionId);
        return ResponseEntity.ok(Map.of(
                "sessionId", session.getSessionId(),
                "status", session.getStatus().toString()
        ));
    }
}