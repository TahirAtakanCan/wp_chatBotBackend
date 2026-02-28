
package com.ihh.wpBot.controller;

import com.ihh.wpBot.model.MediaRequest;
import com.ihh.wpBot.model.SendRequest;
import com.ihh.wpBot.model.SendSession;
import com.ihh.wpBot.service.MessageSendingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
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
    public ResponseEntity<?> startSending(@RequestBody SendRequest request) {
        try {
            // 1. BASE64 MEDYA DOSYALARINI DEKOD EDİP GEÇİCİ KLASÖRE YAZ
            List<String> savedMediaPaths = new ArrayList<>();
            if (request.getMedia() != null && !request.getMedia().isEmpty()) {
                String tempDir = System.getProperty("java.io.tmpdir"); // Bilgisayarın Temp klasörü
                for (MediaRequest mediaReq : request.getMedia()) {
                    // Base64 metnini tekrar Byte dizisine çevir
                    byte[] decodedBytes = Base64.getDecoder().decode(mediaReq.getBase64Data());
                    
                    String uniqueFileName = UUID.randomUUID() + "_" + mediaReq.getFileName();
                    File tempFile = new File(tempDir, uniqueFileName);
                    
                    // Byte dizisini fiziksel dosya olarak kaydet
                    Files.write(tempFile.toPath(), decodedBytes);
                    savedMediaPaths.add(tempFile.getAbsolutePath());
                }
            }

            SendSession session = sendingService.createSession(request.getPhoneNumbers().size());
            
            // 2. KAYDEDİLEN DOSYA YOLLARINI SERVİSE GÖNDER
            sendingService.startSendingProcess(
                    session.getSessionId(), 
                    request.getPhoneNumbers(), 
                    request.getMessage(), 
                    request.getMinDelay(), 
                    request.getMaxDelay(), 
                    savedMediaPaths
            );

            return ResponseEntity.ok(Map.of(
                    "sessionId", session.getSessionId(),
                    "totalNumbers", request.getPhoneNumbers().size(),
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