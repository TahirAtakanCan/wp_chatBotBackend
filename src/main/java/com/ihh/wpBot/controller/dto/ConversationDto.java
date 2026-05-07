package com.ihh.wpBot.controller.dto;

import com.ihh.wpBot.model.Conversation;
import com.ihh.wpBot.model.ConversationStatus;

import java.time.LocalDateTime;

public record ConversationDto(
        Long id,
        String phoneNumber,
        String contactName,
        LocalDateTime lastMessageAt,
        String lastMessageText,
        int unreadCount,
        ConversationStatus status,
        boolean replyWindowOpen
) {
    public static ConversationDto from(Conversation conversation) {
        return new ConversationDto(
                conversation.getId(),
                conversation.getPhoneNumber(),
                conversation.getContactName(),
                conversation.getLastMessageAt(),
                conversation.getLastMessageText(),
                conversation.getUnreadCount(),
                conversation.getStatus(),
                conversation.isReplyWindowOpen()
        );
    }
}

