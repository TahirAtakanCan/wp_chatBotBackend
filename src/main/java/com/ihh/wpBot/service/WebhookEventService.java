package com.ihh.wpBot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ihh.wpBot.model.Conversation;
import com.ihh.wpBot.model.ConversationStatus;
import com.ihh.wpBot.model.DeliveryRecord;
import com.ihh.wpBot.model.DeliveryStatus;
import com.ihh.wpBot.model.Message;
import com.ihh.wpBot.model.MessageDirection;
import com.ihh.wpBot.model.MessageStatus;
import com.ihh.wpBot.model.MessageType;
import com.ihh.wpBot.model.WebhookEvent;
import com.ihh.wpBot.repository.ConversationRepository;
import com.ihh.wpBot.repository.DeliveryRecordRepository;
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
    private final DeliveryRecordRepository deliveryRecordRepository;
    private final TransactionTemplate transactionTemplate;
    private final ZoneId applicationZoneId;
    private final WhatsAppMediaService whatsAppMediaService;
    private final AutoReplyService autoReplyService;

    public WebhookEventService(
            WebhookEventRepository webhookEventRepository,
            ObjectMapper objectMapper,
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            DeliveryRecordRepository deliveryRecordRepository,
            PlatformTransactionManager transactionManager,
            ZoneId applicationZoneId,
            WhatsAppMediaService whatsAppMediaService,
            AutoReplyService autoReplyService
    ) {
        this.webhookEventRepository = webhookEventRepository;
        this.objectMapper = objectMapper;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.deliveryRecordRepository = deliveryRecordRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.applicationZoneId = applicationZoneId;
        this.whatsAppMediaService = whatsAppMediaService;
        this.autoReplyService = autoReplyService;
    }

    public WebhookEvent saveIncomingPayload(String payload) {
        WebhookEvent event = new WebhookEvent();
        event.setReceivedAt(LocalDateTime.now(applicationZoneId));
        event.setPayload(payload);
        event.setEventType("UNKNOWN");

        JsonNode messageNode = null;
        JsonNode statusNode = null;
        String metaContactName = null;

        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode valueNode = root.path("entry").path(0).path("changes").path(0).path("value");

            metaContactName = safeExtractMetaContactName(valueNode);

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
                String finalMetaContactName = metaContactName;
                transactionTemplate.executeWithoutResult(status -> upsertConversationAndInboundMessage(finalMessageNode, finalMetaContactName));
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

    private void upsertConversationAndInboundMessage(JsonNode messageNode, String metaContactName) {
        String fromPhoneRaw = messageNode.path("from").asText(null);
        String phoneNumber = normalizePhoneNumber(fromPhoneRaw);
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(applicationZoneId);

        var existingOpt = conversationRepository.findByPhoneNumber(phoneNumber);
        boolean isNewConversation = existingOpt.isEmpty();

        Conversation conversation = existingOpt.orElseGet(() -> {
            Conversation c = new Conversation();
            c.setPhoneNumber(phoneNumber);
            c.setStatus(ConversationStatus.OPEN);
            c.setCreatedAt(now);
            c.setUnreadCount(0);
            c.setLastMessageAt(now);
            return c;
        });

        if (!isBlank(metaContactName)) {
            if (isNewConversation) {
                conversation.setContactName(metaContactName);
            } else if (isBlank(conversation.getContactName())) {
                conversation.setContactName(metaContactName);
            }
        }

        Message inbound = new Message();
        inbound.setConversation(conversation);
        inbound.setDirection(MessageDirection.INBOUND);
        MessageType messageType = resolveMessageType(messageNode.path("type").asText(null));
        inbound.setMessageType(messageType);
        String content = resolveInboundContent(messageNode, messageType);
        inbound.setContent(content);
        String caption = resolveInboundCaption(messageNode, messageType);
        inbound.setCaption(caption);
        enrichInboundMedia(messageNode, messageType, inbound);
        String waMessageId = messageNode.path("id").asText(null);
        inbound.setWaMessageId(waMessageId);
        inbound.setSentAt(resolveMessageSentAt(messageNode, now, waMessageId));
        inbound.setStatus(MessageStatus.DELIVERED);

        conversation.setLastMessageAt(now);
        conversation.setLastInboundAt(now);
        conversation.setLastMessageText(truncate(resolveConversationPreview(messageType, content), 500));
        conversation.setLastMessageType(messageType);
        conversation.setUnreadCount(conversation.getUnreadCount() + 1);
        if (conversation.getStatus() == ConversationStatus.CLOSED) {
            conversation.setStatus(ConversationStatus.OPEN);
        }

        Conversation savedConversation = conversationRepository.save(conversation);
        inbound.setConversation(savedConversation);
        messageRepository.save(inbound);

        // Otomatik yanıt — sadece text mesajlar için, hata ana akışı kırmasın
        if (messageType == MessageType.TEXT) {
            try {
                autoReplyService.processIncomingMessage(phoneNumber, content);
            } catch (Exception e) {
                log.error("Auto-reply processing failed for phone={}", phoneNumber, e);
            }
        }
    }

    private String safeExtractMetaContactName(JsonNode valueNode) {
        if (valueNode == null) {
            return null;
        }
        JsonNode contactsNode = valueNode.path("contacts");
        if (!contactsNode.isArray() || contactsNode.isEmpty()) {
            return null;
        }
        JsonNode contact0 = contactsNode.get(0);
        if (contact0 == null || contact0.isNull()) {
            return null;
        }
        String name = contact0.path("profile").path("name").asText(null);
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private void updateOutboundMessageStatus(JsonNode statusNode) {
        String waMessageId = statusNode.path("id").asText(null);
        if (waMessageId == null || waMessageId.isBlank()) {
            return;
        }

        String statusRaw = statusNode.path("status").asText(null);
        MessageStatus newStatus = mapMessageStatus(statusRaw);
        if (newStatus != null) {
            messageRepository.findByWaMessageId(waMessageId).ifPresentOrElse(msg -> {
                msg.setStatus(newStatus);
                messageRepository.save(msg);
                log.info("Message status updated. waMessageId={}, status={}", waMessageId, newStatus);
            }, () -> log.info("Message not found for status update. waMessageId={}", waMessageId));
        }

        try {
            deliveryRecordRepository.findByWaMessageId(waMessageId).ifPresentOrElse(record -> {
                LocalDateTime now = LocalDateTime.now(applicationZoneId);
                applyDeliveryStatusUpdate(record, statusRaw, statusNode.path("errors"), now);
                deliveryRecordRepository.save(record);
                log.info("DeliveryRecord updated. waMessageId={}, status={}", waMessageId, statusRaw);
            }, () -> log.debug("DeliveryRecord not found for status update: {}", waMessageId));
        } catch (Exception e) {
            log.warn("DeliveryRecord güncellenirken hata: {}", e.getMessage());
        }
    }

    private void applyDeliveryStatusUpdate(DeliveryRecord record, String statusRaw, JsonNode errorsNode, LocalDateTime now) {
        if (statusRaw == null) {
            return;
        }
        switch (statusRaw.trim().toLowerCase()) {
            case "delivered":
                record.setStatus(DeliveryStatus.DELIVERED);
                if (record.getDeliveredAt() == null) {
                    record.setDeliveredAt(now);
                }
                break;
            case "read":
                record.setStatus(DeliveryStatus.READ);
                if (record.getReadAt() == null) {
                    record.setReadAt(now);
                }
                break;
            case "failed":
                record.setStatus(DeliveryStatus.FAILED);
                if (record.getFailedAt() == null) {
                    record.setFailedAt(now);
                }
                if (errorsNode != null && errorsNode.isArray() && !errorsNode.isEmpty()) {
                    JsonNode firstError = errorsNode.get(0);
                    if (firstError.hasNonNull("code")) {
                        record.setFailureCode(firstError.path("code").asText(null));
                    }
                    String title = firstError.path("title").asText(null);
                    if (title != null && !title.isBlank()) {
                        record.setFailureReason(title);
                    }
                }
                break;
            default:
                // SENT state is already marked when message is accepted by Meta.
                break;
        }
    }

    private MessageType resolveMessageType(String type) {
        if (type == null) {
            return MessageType.TEXT;
        }
        String t = type.trim().toLowerCase();
        return switch (t) {
            case "image" -> MessageType.IMAGE;
            case "video" -> MessageType.VIDEO;
            case "audio" -> MessageType.AUDIO;
            case "document" -> MessageType.DOCUMENT;
            case "sticker" -> MessageType.STICKER;
            case "text" -> MessageType.TEXT;
            default -> MessageType.TEXT;
        };
    }

    private String resolveInboundContent(JsonNode messageNode, MessageType messageType) {
        if (messageType == MessageType.TEXT) {
            String body = messageNode.path("text").path("body").asText(null);
            if (body != null && !body.isBlank()) {
                return body;
            }
            return "";
        }

        String caption = resolveInboundCaption(messageNode, messageType);
        if (caption != null && !caption.isBlank()) {
            return caption;
        }
        return "";
    }

    private String resolveInboundCaption(JsonNode messageNode, MessageType messageType) {
        JsonNode mediaNode = resolveMediaNode(messageNode, messageType);
        if (mediaNode == null || mediaNode.isMissingNode()) {
            return null;
        }
        String caption = mediaNode.path("caption").asText(null);
        if (caption == null || caption.isBlank()) {
            return null;
        }
        return caption;
    }

    private void enrichInboundMedia(JsonNode messageNode, MessageType messageType, Message inbound) {
        JsonNode mediaNode = resolveMediaNode(messageNode, messageType);
        if (mediaNode == null || mediaNode.isMissingNode()) {
            return;
        }
        String mediaId = mediaNode.path("id").asText(null);
        if (mediaId == null || mediaId.isBlank()) {
            return;
        }
        inbound.setMediaId(mediaId);
        inbound.setMediaUrl("/api/media/" + mediaId);

        String mimeType = mediaNode.path("mime_type").asText(null);
        if (mimeType != null && !mimeType.isBlank()) {
            inbound.setMimeType(mimeType);
        }
        String filename = mediaNode.path("filename").asText(null);
        if (filename != null && !filename.isBlank()) {
            inbound.setMediaFilename(filename);
        }

        whatsAppMediaService.downloadIncomingMedia(mediaId).ifPresent(storedMedia -> {
            inbound.setMediaStoragePath(storedMedia.storagePath());
            if (storedMedia.mimeType() != null && !storedMedia.mimeType().isBlank()) {
                inbound.setMimeType(storedMedia.mimeType());
            }
        });
    }

    private JsonNode resolveMediaNode(JsonNode messageNode, MessageType messageType) {
        return switch (messageType) {
            case IMAGE -> messageNode.path("image");
            case VIDEO -> messageNode.path("video");
            case AUDIO -> messageNode.path("audio");
            case DOCUMENT -> messageNode.path("document");
            case STICKER -> messageNode.path("sticker");
            default -> null;
        };
    }

    private String resolveConversationPreview(MessageType messageType, String content) {
        if (messageType == null) {
            return content;
        }
        return switch (messageType) {
            case IMAGE -> "📷 Fotoğraf";
            case VIDEO -> "🎬 Video";
            case AUDIO -> "🎵 Ses";
            case DOCUMENT -> "📄 Belge";
            case STICKER -> "🧩 Sticker";
            default -> content;
        };
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
                applicationZoneId
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
