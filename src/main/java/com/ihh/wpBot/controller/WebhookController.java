package com.ihh.wpBot.controller;

import com.ihh.wpBot.model.WebhookEvent;
import com.ihh.wpBot.service.WebhookEventService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhook")
public class WebhookController {

    private final WebhookEventService webhookEventService;

    public WebhookController(WebhookEventService webhookEventService) {
        this.webhookEventService = webhookEventService;
    }

    @Value("${meta.webhook.verify-token}")
    private String verifyToken;

    @GetMapping
    public ResponseEntity<String> verifyWebhook(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String incomingToken,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {

        if ("subscribe".equals(mode) && this.verifyToken.equals(incomingToken)) {
            return ResponseEntity.ok(challenge);
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @PostMapping
    public ResponseEntity<String> receiveWebhook(@RequestBody String payload) {
        WebhookEvent event = webhookEventService.saveIncomingPayload(payload);
        System.out.println("Gelen Webhook İstek: ");
        System.out.println(payload);
        System.out.println("Webhook Event Kaydedildi -> id=" + event.getId()
                + ", type=" + event.getEventType()
                + ", status=" + event.getMessageStatus());
        return ResponseEntity.ok("OK");
    }
}