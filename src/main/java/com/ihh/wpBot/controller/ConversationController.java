package com.ihh.wpBot.controller;

import com.ihh.wpBot.controller.dto.ConversationDto;
import com.ihh.wpBot.controller.dto.ErrorResponse;
import com.ihh.wpBot.controller.dto.ImageReplyRequest;
import com.ihh.wpBot.controller.dto.MessageDto;
import com.ihh.wpBot.controller.dto.ReplyRequest;
import com.ihh.wpBot.model.Conversation;
import com.ihh.wpBot.model.ConversationStatus;
import com.ihh.wpBot.model.Message;
import com.ihh.wpBot.model.MessageDirection;
import com.ihh.wpBot.model.MessageStatus;
import com.ihh.wpBot.model.MessageType;
import com.ihh.wpBot.repository.ConversationRepository;
import com.ihh.wpBot.repository.MessageRepository;
import com.ihh.wpBot.service.WhatsAppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.nio.file.Path;
import java.nio.file.Files;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {
    private static final Logger log = LoggerFactory.getLogger(ConversationController.class);

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final WhatsAppService whatsAppService;
    private final ZoneId applicationZoneId;

    public ConversationController(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            WhatsAppService whatsAppService,
            ZoneId applicationZoneId
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.whatsAppService = whatsAppService;
        this.applicationZoneId = applicationZoneId;
    }

    @GetMapping
    public ResponseEntity<Page<ConversationDto>> listConversations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<ConversationDto> result = conversationRepository
                .findAllByOrderByLastMessageAtDesc(pageable)
                .map(ConversationDto::from);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/messages")
    @Transactional
    public ResponseEntity<?> listMessages(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size
    ) {
        Conversation conversation = conversationRepository.findById(id).orElse(null);
        if (conversation == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        conversation.setUnreadCount(0);
        conversationRepository.save(conversation);

        PageRequest pageable = PageRequest.of(page, size);
        Page<MessageDto> result = messageRepository
                .findByConversationIdOrderBySentAtAsc(id, pageable)
                .map(MessageDto::from);

        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/reply")
    @Transactional
    public ResponseEntity<?> reply(
            @PathVariable Long id,
            @RequestBody ReplyRequest request
    ) {
        String rawText = request != null ? request.text() : null;
        log.info("[REPLY DEBUG] conversationId={}, text=[{}], length={}, codePoints={}, bytesHex={}",
                id,
                rawText,
                rawText == null ? -1 : rawText.length(),
                rawText == null ? -1 : rawText.codePointCount(0, rawText.length()),
                rawText == null ? "null" : bytesToHex(rawText.getBytes(StandardCharsets.UTF_8))
        );

        Conversation conversation = conversationRepository.findById(id).orElse(null);
        if (conversation == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        if (!conversation.isReplyWindowOpen()) {
            return ResponseEntity.unprocessableEntity().body(
                    new ErrorResponse(
                            "REPLY_WINDOW_CLOSED",
                            "24 saatlik müşteri penceresi kapanmış. Yalnızca onaylı template gönderebilirsiniz."
                    )
            );
        }

        String waMessageId;
        try {
            waMessageId = whatsAppService.sendTextMessage(conversation.getPhoneNumber(), request.text());
        } catch (IllegalStateException e) {
            if ("RATE_LIMITED".equals(e.getMessage())) {
                return ResponseEntity.status(429).body(ErrorResponse.of("RATE_LIMITED"));
            }
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
                    new ErrorResponse("WHATSAPP_API_ERROR", e.getMessage())
            );
        }

        LocalDateTime now = LocalDateTime.now(applicationZoneId);

        Message message = new Message();
        message.setConversation(conversation);
        message.setDirection(MessageDirection.OUTBOUND);
        message.setMessageType(MessageType.TEXT);
        message.setContent(request.text());
        message.setWaMessageId(waMessageId);
        message.setSentAt(now);
        message.setStatus(MessageStatus.SENT);
        log.info("[DB DEBUG] Message will be saved with content=[{}], length={}",
                message.getContent(),
                message.getContent() == null ? -1 : message.getContent().length()
        );
        Message savedMessage = messageRepository.save(message);

        conversation.setLastMessageAt(now);
        conversation.setLastMessageText(truncate(request.text(), 500));
        conversation.setLastMessageType(MessageType.TEXT);
        conversationRepository.save(conversation);

        return ResponseEntity.ok(MessageDto.from(savedMessage));
    }

    @PostMapping("/{id}/reply-image")
    @Transactional
    public ResponseEntity<?> replyImage(
            @PathVariable Long id,
            @RequestBody ImageReplyRequest request
    ) {
        Conversation conversation = conversationRepository.findById(id).orElse(null);
        if (conversation == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        if (!conversation.isReplyWindowOpen()) {
            return ResponseEntity.unprocessableEntity().body(
                    new ErrorResponse(
                            "REPLY_WINDOW_CLOSED",
                            "24 saatlik müşteri penceresi kapanmış. Yalnızca onaylı template gönderebilirsiniz."
                    )
            );
        }

        String imageUrl = request.imageUrl();
        if (imageUrl == null || imageUrl.isBlank()) {
            return ResponseEntity.badRequest().body(
                    new ErrorResponse("IMAGE_URL_REQUIRED", "imageUrl zorunludur.")
            );
        }

        String waMessageId;
        try {
            waMessageId = whatsAppService.sendImageMessage(conversation.getPhoneNumber(), imageUrl, request.caption());
        } catch (IllegalStateException e) {
            if ("RATE_LIMITED".equals(e.getMessage())) {
                return ResponseEntity.status(429).body(ErrorResponse.of("RATE_LIMITED"));
            }
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
                    new ErrorResponse("WHATSAPP_API_ERROR", e.getMessage())
            );
        }

        LocalDateTime now = LocalDateTime.now(applicationZoneId);
        String trimmedCaption = request.caption() != null ? request.caption().trim() : null;

        Message message = new Message();
        message.setConversation(conversation);
        message.setDirection(MessageDirection.OUTBOUND);
        message.setMessageType(MessageType.IMAGE);
        message.setContent(trimmedCaption == null ? "" : trimmedCaption);
        message.setCaption(trimmedCaption);
        message.setMediaUrl(imageUrl.trim());
        message.setMediaId(extractMediaIdFromUrl(imageUrl));
        message.setWaMessageId(waMessageId);
        message.setSentAt(now);
        message.setStatus(MessageStatus.SENT);
        Message savedMessage = messageRepository.save(message);

        conversation.setLastMessageAt(now);
        conversation.setLastMessageType(MessageType.IMAGE);
        conversation.setLastMessageText("📷 Fotoğraf");
        conversationRepository.save(conversation);

        return ResponseEntity.ok(MessageDto.from(savedMessage));
    }

    @PostMapping("/{id}/send-contact-card")
    @Transactional
    public ResponseEntity<?> sendContactCard(@PathVariable Long id) {
        Conversation conversation = conversationRepository.findById(id).orElse(null);
        if (conversation == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        if (!conversation.isReplyWindowOpen()) {
            return ResponseEntity.unprocessableEntity().body(
                    new ErrorResponse(
                            "REPLY_WINDOW_CLOSED",
                            "24 saatlik müşteri penceresi kapanmış. Yalnızca onaylı template gönderebilirsiniz."
                    )
            );
        }

        String waMessageId;
        try {
            waMessageId = whatsAppService.sendContactCard(conversation.getPhoneNumber());
        } catch (IllegalStateException e) {
            if ("RATE_LIMITED".equals(e.getMessage())) {
                return ResponseEntity.status(429).body(ErrorResponse.of("RATE_LIMITED"));
            }
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
                    new ErrorResponse("WHATSAPP_API_ERROR", e.getMessage())
            );
        }

        LocalDateTime now = LocalDateTime.now(applicationZoneId);

        Message message = new Message();
        message.setConversation(conversation);
        message.setDirection(MessageDirection.OUTBOUND);
        message.setMessageType(MessageType.TEXT);
        message.setContent("📇 Kişi Kartı Gönderildi");
        message.setWaMessageId(waMessageId);
        message.setSentAt(now);
        message.setStatus(MessageStatus.SENT);
        Message savedMessage = messageRepository.save(message);

        conversation.setLastMessageAt(now);
        conversation.setLastMessageText("📇 Kişi Kartı");
        conversation.setLastMessageType(MessageType.TEXT);
        conversationRepository.save(conversation);

        return ResponseEntity.ok(MessageDto.from(savedMessage));
    }

    @PutMapping("/{id}/close")
    @Transactional
    public ResponseEntity<?> close(@PathVariable Long id) {
        Conversation conversation = conversationRepository.findById(id).orElse(null);
        if (conversation == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        conversation.setStatus(ConversationStatus.CLOSED);
        Conversation saved = conversationRepository.save(conversation);
        return ResponseEntity.ok(ConversationDto.from(saved));
    }

    @DeleteMapping("/{id}/messages/{messageId}")
    @Transactional
    public ResponseEntity<?> deleteMessage(
            @PathVariable Long id,
            @PathVariable Long messageId
    ) {
        Conversation conversation = conversationRepository.findById(id).orElse(null);
        if (conversation == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    new ErrorResponse("CONVERSATION_NOT_FOUND", "Conversation bulunamadı.")
            );
        }

        Optional<Message> messageOpt = messageRepository.findByIdAndConversationId(messageId, id);
        if (messageOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    new ErrorResponse("MESSAGE_NOT_FOUND", "Mesaj bu conversation'da bulunamadı.")
            );
        }

        messageRepository.delete(messageOpt.get());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/messages")
    @Transactional
    public ResponseEntity<?> clearMessages(@PathVariable Long id) {
        Conversation conversation = conversationRepository.findById(id).orElse(null);
        if (conversation == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    new ErrorResponse("CONVERSATION_NOT_FOUND", "Conversation bulunamadı.")
            );
        }

        long deleted = messageRepository.deleteByConversationId(id);

        conversation.setLastMessageText(null);
        conversation.setLastMessageType(null);
        conversation.setUnreadCount(0);
        conversationRepository.save(conversation);

        return ResponseEntity.ok(Map.of("deleted", deleted));
    }

    @GetMapping("/messages/{messageId}/media")
    public ResponseEntity<Resource> getMessageMedia(@PathVariable Long messageId) {
        return messageRepository.findByIdAndMediaStoragePathIsNotNull(messageId)
                .map(this::mediaResponse)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> deleteConversation(@PathVariable Long id) {
        Conversation conversation = conversationRepository.findById(id).orElse(null);
        if (conversation == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    new ErrorResponse("CONVERSATION_NOT_FOUND", "Conversation bulunamadı.")
            );
        }

        long deletedMessages = messageRepository.deleteByConversationId(id);
        conversationRepository.delete(conversation);

        return ResponseEntity.ok(Map.of(
                "deletedConversationId", id,
                "deletedMessages", deletedMessages
        ));
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

    private ResponseEntity<Resource> mediaResponse(Message message) {
        try {
            Path path = Path.of(message.getMediaStoragePath());
            if (!Files.exists(path) || !Files.isReadable(path)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            MediaType mediaType = resolveMediaType(message.getMimeType());
            Resource resource = new PathResource(path);
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + path.getFileName() + "\"")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private MediaType resolveMediaType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(mimeType);
        } catch (Exception ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private String extractMediaIdFromUrl(String mediaUrl) {
        if (mediaUrl == null) {
            return null;
        }
        String url = mediaUrl.trim();
        int queryIdx = url.indexOf('?');
        if (queryIdx >= 0) {
            url = url.substring(0, queryIdx);
        }
        int mediaIdx = url.lastIndexOf("/api/media/");
        if (mediaIdx < 0) {
            return null;
        }
        String suffix = url.substring(mediaIdx + "/api/media/".length());
        if (suffix.startsWith("public/")) {
            suffix = suffix.substring("public/".length());
        }
        return suffix.isBlank() ? null : suffix;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x ", b));
        }
        return sb.toString().trim();
    }
}

