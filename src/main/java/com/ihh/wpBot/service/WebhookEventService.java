package com.ihh.wpBot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ihh.wpBot.model.WebhookEvent;
import com.ihh.wpBot.repository.WebhookEventRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class WebhookEventService {

    private final WebhookEventRepository webhookEventRepository;
    private final ObjectMapper objectMapper;

    public WebhookEventService(WebhookEventRepository webhookEventRepository, ObjectMapper objectMapper) {
        this.webhookEventRepository = webhookEventRepository;
        this.objectMapper = objectMapper;
    }

    public WebhookEvent saveIncomingPayload(String payload) {
        WebhookEvent event = new WebhookEvent();
        event.setReceivedAt(LocalDateTime.now());
        event.setPayload(payload);
        event.setEventType("UNKNOWN");

        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode valueNode = root.path("entry").path(0).path("changes").path(0).path("value");

            JsonNode statusesNode = valueNode.path("statuses");
            if (statusesNode.isArray() && statusesNode.size() > 0) {
                JsonNode status = statusesNode.get(0);
                event.setEventType("STATUS");
                event.setWaMessageId(status.path("id").asText(null));
                event.setMessageStatus(status.path("status").asText(null));
                event.setFromPhone(status.path("recipient_id").asText(null));
                return webhookEventRepository.save(event);
            }

            JsonNode messagesNode = valueNode.path("messages");
            if (messagesNode.isArray() && messagesNode.size() > 0) {
                JsonNode message = messagesNode.get(0);
                event.setEventType("MESSAGE");
                event.setWaMessageId(message.path("id").asText(null));
                event.setMessageStatus("received");
                event.setFromPhone(message.path("from").asText(null));
                return webhookEventRepository.save(event);
            }
        } catch (Exception e) {
            event.setEventType("PARSE_ERROR");
            event.setMessageStatus("invalid_payload");
        }

        return webhookEventRepository.save(event);
    }
}
