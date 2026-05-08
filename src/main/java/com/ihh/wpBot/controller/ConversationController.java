package com.ihh.wpBot.controller;

import com.ihh.wpBot.controller.dto.ConversationDto;
import com.ihh.wpBot.controller.dto.ErrorResponse;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final WhatsAppService whatsAppService;

    public ConversationController(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            WhatsAppService whatsAppService
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.whatsAppService = whatsAppService;
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

        LocalDateTime now = LocalDateTime.now();

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

        LocalDateTime now = LocalDateTime.now();

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

