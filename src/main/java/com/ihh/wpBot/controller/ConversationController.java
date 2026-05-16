package com.ihh.wpBot.controller;

import com.ihh.wpBot.controller.dto.ConversationDto;
import com.ihh.wpBot.controller.dto.ErrorResponse;
import com.ihh.wpBot.controller.dto.MessageDto;
import com.ihh.wpBot.controller.dto.ReplyDocumentRequest;
import com.ihh.wpBot.controller.dto.ReplyMediaRequest;
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
import java.util.Map;
import java.util.Optional;
import java.nio.file.Path;
import java.nio.file.Files;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {
    private static final long MAX_INLINE_VIDEO_BYTES = 16L * 1024 * 1024;
    private static final long MAX_DOCUMENT_BYTES = 100L * 1024 * 1024;

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
            @RequestBody ReplyMediaRequest request
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

        String imageUrl = request.mediaUrl();
        if (imageUrl == null || imageUrl.isBlank()) {
            return ResponseEntity.badRequest().body(
                    new ErrorResponse("IMAGE_URL_REQUIRED", "mediaUrl zorunludur.")
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

    @PostMapping("/{id}/reply-video")
    @Transactional
    public ResponseEntity<?> replyVideo(
            @PathVariable Long id,
            @RequestBody ReplyMediaRequest request
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

        String videoUrl = request.mediaUrl();
        if (videoUrl == null || videoUrl.isBlank()) {
            return ResponseEntity.badRequest().body(
                    new ErrorResponse("VIDEO_URL_REQUIRED", "mediaUrl zorunludur.")
            );
        }

        Long sizeBytes = request.sizeBytes();
        if (sizeBytes != null && sizeBytes >= MAX_INLINE_VIDEO_BYTES) {
            return ResponseEntity.unprocessableEntity().body(
                    new ErrorResponse("VIDEO_TOO_LARGE_FOR_INLINE", "16 MB üzerindeki videoları belge olarak gönderin.")
            );
        }

        String waMessageId;
        try {
            waMessageId = whatsAppService.sendVideoMessage(conversation.getPhoneNumber(), videoUrl, request.caption());
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
        message.setMessageType(MessageType.VIDEO);
        message.setContent(trimmedCaption == null ? "" : trimmedCaption);
        message.setCaption(trimmedCaption);
        message.setMediaUrl(videoUrl.trim());
        message.setMediaId(extractMediaIdFromUrl(videoUrl));
        message.setWaMessageId(waMessageId);
        message.setSentAt(now);
        message.setStatus(MessageStatus.SENT);
        Message savedMessage = messageRepository.save(message);

        conversation.setLastMessageAt(now);
        conversation.setLastMessageType(MessageType.VIDEO);
        conversation.setLastMessageText("🎬 Video");
        conversationRepository.save(conversation);

        return ResponseEntity.ok(MessageDto.from(savedMessage));
    }

    @PostMapping("/{id}/reply-document")
    @Transactional
    public ResponseEntity<?> replyDocument(
            @PathVariable Long id,
            @RequestBody ReplyDocumentRequest request
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

        String documentUrl = request.mediaUrl();
        if (documentUrl == null || documentUrl.isBlank()) {
            return ResponseEntity.badRequest().body(
                    new ErrorResponse("DOCUMENT_URL_REQUIRED", "mediaUrl zorunludur.")
            );
        }
        if (request.filename() == null || request.filename().isBlank()) {
            return ResponseEntity.badRequest().body(
                    new ErrorResponse("FILENAME_REQUIRED", "filename zorunludur.")
            );
        }

        Long sizeBytes = request.sizeBytes();
        if (sizeBytes != null && sizeBytes > MAX_DOCUMENT_BYTES) {
            return ResponseEntity.unprocessableEntity().body(
                    new ErrorResponse("DOCUMENT_TOO_LARGE", "100 MB üzerindeki dosyalar gönderilemez.")
            );
        }

        String waMessageId;
        try {
            waMessageId = whatsAppService.sendDocumentMessage(
                    conversation.getPhoneNumber(),
                    documentUrl,
                    request.filename(),
                    request.caption()
            );
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
        message.setMessageType(MessageType.DOCUMENT);
        message.setContent(trimmedCaption == null ? "" : trimmedCaption);
        message.setCaption(trimmedCaption);
        message.setMediaUrl(documentUrl.trim());
        message.setMediaId(extractMediaIdFromUrl(documentUrl));
        message.setMediaFilename(request.filename().trim());
        message.setWaMessageId(waMessageId);
        message.setSentAt(now);
        message.setStatus(MessageStatus.SENT);
        Message savedMessage = messageRepository.save(message);

        conversation.setLastMessageAt(now);
        conversation.setLastMessageType(MessageType.DOCUMENT);
        conversation.setLastMessageText("📄 Belge");
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
        return messageRepository.findById(messageId)
                .map(this::resolveMediaResponse)
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

    private ResponseEntity<Resource> resolveMediaResponse(Message message) {
        if (message.getMediaStoragePath() != null && !message.getMediaStoragePath().isBlank()) {
            return mediaResponse(message);
        }
        try {
            String uploadFilename = resolveUploadFilename(message);
            if (uploadFilename == null || uploadFilename.isBlank()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Path path = Path.of("uploads").resolve(uploadFilename).normalize();
            if (!Files.exists(path) || !Files.isReadable(path)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Resource resource = new PathResource(path);
            MediaType mediaType = resolveMediaType(message.getMimeType());
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + path.getFileName() + "\"")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private String resolveUploadFilename(Message message) {
        if (message.getMediaId() != null && !message.getMediaId().isBlank()) {
            return message.getMediaId().trim();
        }
        String mediaUrl = message.getMediaUrl();
        if (mediaUrl == null || mediaUrl.isBlank()) {
            return null;
        }
        String normalizedUrl = mediaUrl.trim();
        int queryIndex = normalizedUrl.indexOf('?');
        if (queryIndex >= 0) {
            normalizedUrl = normalizedUrl.substring(0, queryIndex);
        }
        int marker = normalizedUrl.lastIndexOf("/api/media/public/");
        if (marker >= 0) {
            String filePart = normalizedUrl.substring(marker + "/api/media/public/".length());
            return filePart.isBlank() ? null : filePart;
        }
        int lastSlash = normalizedUrl.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash + 1 < normalizedUrl.length()) {
            return normalizedUrl.substring(lastSlash + 1);
        }
        return null;
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
}

