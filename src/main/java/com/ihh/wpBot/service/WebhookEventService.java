package com.ihh.wpBot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ihh.wpBot.model.Conversation;
import com.ihh.wpBot.model.ConversationStatus;
import com.ihh.wpBot.model.Message;
import com.ihh.wpBot.model.MessageDirection;
import com.ihh.wpBot.model.MessageStatus;
import com.ihh.wpBot.model.MessageType;
import com.ihh.wpBot.model.WebhookEvent;
import com.ihh.wpBot.repository.ConversationRepository;
import com.ihh.wpBot.repository.MessageRepository;
import com.ihh.wpBot.repository.WebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class WebhookEventService {

    private static final Logger log = LoggerFactory.getLogger(WebhookEventService.class);

    private final WebhookEventRepository webhookEventRepository;
    private final ObjectMapper objectMapper;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final TransactionTemplate transactionTemplate;

    public WebhookEventService(
            WebhookEventRepository webhookEventRepository,
            ObjectMapper objectMapper,
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.webhookEventRepository = webhookEventRepository;
        this.objectMapper = objectMapper;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public WebhookEvent saveIncomingPayload(String payload) {
        WebhookEvent event = new WebhookEvent();
        event.setReceivedAt(LocalDateTime.now());
        event.setPayload(payload);
        event.setEventType("UNKNOWN");

        JsonNode messageNode = null;
        JsonNode statusNode = null;

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
                statusNode = status;
            }

            JsonNode messagesNode = valueNode.path("messages");
            if (!"STATUS".equals(event.getEventType()) && messagesNode.isArray() && messagesNode.size() > 0) {
                JsonNode message = messagesNode.get(0);
                event.setEventType("MESSAGE");
                event.setWaMessageId(message.path("id").asText(null));
                event.setMessageStatus("received");
                event.setFromPhone(message.path("from").asText(null));
                messageNode = message;
            }
        } catch (Exception e) {
            event.setEventType("PARSE_ERROR");
            event.setMessageStatus("invalid_payload");
        }

        WebhookEvent saved = webhookEventRepository.save(event);
        log.info("WebhookEvent saved. id={}, type={}, waMessageId={}", saved.getId(), saved.getEventType(), saved.getWaMessageId());

        if ("MESSAGE".equals(saved.getEventType()) && messageNode != null) {
            try {
                JsonNode finalMessageNode = messageNode;
                transactionTemplate.executeWithoutResult(status -> upsertConversationAndInboundMessage(finalMessageNode));
                log.info("Conversation upserted. phone={}", normalizePhoneNumber(saved.getFromPhone()));
            } catch (Exception e) {
                log.error("Conversation/Message write failed for MESSAGE event. waMessageId={}", saved.getWaMessageId(), e);
            }
        } else if ("STATUS".equals(saved.getEventType()) && statusNode != null) {
            try {
                JsonNode finalStatusNode = statusNode;
                transactionTemplate.executeWithoutResult(status -> updateOutboundMessageStatus(finalStatusNode));
            } catch (Exception e) {
                log.error("Message status update failed for STATUS event. waMessageId={}", saved.getWaMessageId(), e);
            }
        }

        return saved;
    }

    private void upsertConversationAndInboundMessage(JsonNode messageNode) {
        String fromPhoneRaw = messageNode.path("from").asText(null);
        String phoneNumber = normalizePhoneNumber(fromPhoneRaw);
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        Conversation conversation = conversationRepository.findByPhoneNumber(phoneNumber).orElseGet(() -> {
            Conversation c = new Conversation();
            c.setPhoneNumber(phoneNumber);
            c.setStatus(ConversationStatus.OPEN);
            c.setCreatedAt(now);
            c.setUnreadCount(0);
            c.setLastMessageAt(now);
            return c;
        });

        Message inbound = new Message();
        inbound.setConversation(conversation);
        inbound.setDirection(MessageDirection.INBOUND);
        inbound.setMessageType(resolveMessageType(messageNode.path("type").asText(null)));
        String content = resolveInboundContent(messageNode);
        inbound.setContent(content);
        String waMessageId = messageNode.path("id").asText(null);
        inbound.setWaMessageId(waMessageId);
        inbound.setSentAt(resolveMessageSentAt(messageNode, now, waMessageId));
        inbound.setStatus(MessageStatus.DELIVERED);

        conversation.setLastMessageAt(now);
        conversation.setLastInboundAt(now);
        conversation.setLastMessageText(truncate(content, 500));
        conversation.setUnreadCount(conversation.getUnreadCount() + 1);
        if (conversation.getStatus() == ConversationStatus.CLOSED) {
            conversation.setStatus(ConversationStatus.OPEN);
        }

        Conversation savedConversation = conversationRepository.save(conversation);
        inbound.setConversation(savedConversation);
        messageRepository.save(inbound);
    }

    private void updateOutboundMessageStatus(JsonNode statusNode) {
        String waMessageId = statusNode.path("id").asText(null);
        if (waMessageId == null || waMessageId.isBlank()) {
            return;
        }

        MessageStatus newStatus = mapMessageStatus(statusNode.path("status").asText(null));
        if (newStatus == null) {
            return;
        }

        messageRepository.findByWaMessageId(waMessageId).ifPresentOrElse(msg -> {
            msg.setStatus(newStatus);
            messageRepository.save(msg);
            log.info("Message status updated. waMessageId={}, status={}", waMessageId, newStatus);
        }, () -> log.info("Message not found for status update. waMessageId={}", waMessageId));
    }

    private MessageType resolveMessageType(String type) {
        if (type == null) {
            return MessageType.TEXT;
        }
        String t = type.trim().toLowerCase();
        if ("image".equals(t)) {
            return MessageType.IMAGE;
        }
        if ("text".equals(t)) {
            return MessageType.TEXT;
        }
        return MessageType.TEXT;
    }

    private String resolveInboundContent(JsonNode messageNode) {
        String type = messageNode.path("type").asText(null);
        if (type != null) {
            String t = type.trim().toLowerCase();
            if ("text".equals(t)) {
                String body = messageNode.path("text").path("body").asText(null);
                if (body != null && !body.isBlank()) {
                    return body;
                }
            }
            if ("image".equals(t)) {
                String caption = messageNode.path("image").path("caption").asText(null);
                if (caption != null && !caption.isBlank()) {
                    return caption;
                }
                return "[medya]";
            }
        }

        String fallback = messageNode.path("text").path("body").asText(null);
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return "[medya]";
    }

    private static final LocalDateTime MIN_REASONABLE_TIMESTAMP = LocalDateTime.of(2020, 1, 1, 0, 0);
    private static final LocalDateTime MAX_REASONABLE_TIMESTAMP = LocalDateTime.of(2030, 1, 1, 0, 0);

    private LocalDateTime resolveMessageSentAt(JsonNode messageNode, LocalDateTime fallback, String waMessageId) {
        String timestampStr = messageNode.path("timestamp").asText(null);
        if (timestampStr == null || timestampStr.isBlank()) {
            log.warn("Meta timestamp missing for waMessageId={}, using current time as fallback", waMessageId);
            return fallback;
        }

        final long epochSeconds;
        try {
            epochSeconds = Long.parseLong(timestampStr);
        } catch (NumberFormatException e) {
            log.error("Failed to parse Meta timestamp '{}' for waMessageId={}, using current time", timestampStr, waMessageId);
            return fallback;
        }

        LocalDateTime sentAt = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(epochSeconds),
                ZoneId.systemDefault()
        );

        if (sentAt.isBefore(MIN_REASONABLE_TIMESTAMP) || !sentAt.isBefore(MAX_REASONABLE_TIMESTAMP)) {
            log.warn("Suspicious timestamp from Meta: {}, falling back to now (waMessageId={})", sentAt, waMessageId);
            return fallback;
        }

        return sentAt;
    }

    private MessageStatus mapMessageStatus(String status) {
        if (status == null) {
            return null;
        }
        String s = status.trim().toUpperCase();
        try {
            return MessageStatus.valueOf(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String normalizePhoneNumber(String phone) {
        if (phone == null) {
            return null;
        }
        String normalized = phone.trim().replace(" ", "").replace("-", "");
        if (normalized.isBlank()) {
            return normalized;
        }
        if (!normalized.startsWith("+")) {
            normalized = "+" + normalized;
        }
        return normalized;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) {
            return null;
        }
        if (s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, maxLen);
    }
}
