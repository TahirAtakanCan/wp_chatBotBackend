
package com.ihh.wpBot.controller;

import com.ihh.wpBot.model.MediaRequest;
import com.ihh.wpBot.model.SendRequest;
import com.ihh.wpBot.model.SendSession;
import com.ihh.wpBot.service.MessageSendingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import java.util.ArrayList;
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

    // startSending metodunun içini şu şekilde değiştir:
    @PostMapping("/start")
    public ResponseEntity<?> startSending(@RequestBody SendRequest request) {
        try {
            SendSession session = sendingService.createSession(request.getPhoneNumbers().size());
            
            // Flutter'dan gelen URL listesini (request.getMedia()) direkt servise yolluyoruz!
            List<String> mediaUrls = new ArrayList<>();
            if (request.getMedia() != null) {
                for (MediaRequest media : request.getMedia()) {
                    mediaUrls.add(media.getUrl());
                }
            }
            
            sendingService.startSendingProcess(
                    session.getSessionId(), 
                    request.getPhoneNumbers(), 
                    request.getMessage(), 
                    request.getMinDelay(), 
                    request.getMaxDelay(), 
                    mediaUrls 
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