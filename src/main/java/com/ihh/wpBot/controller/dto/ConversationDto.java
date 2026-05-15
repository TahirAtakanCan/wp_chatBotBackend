package com.ihh.wpBot.controller.dto;

import com.ihh.wpBot.model.Conversation;
import com.ihh.wpBot.model.ConversationStatus;
import com.ihh.wpBot.model.MessageType;

import java.time.LocalDateTime;

public record ConversationDto(
        Long id,
        String phoneNumber,
        String contactName,
        LocalDateTime lastMessageAt,
        String lastMessageText,
        MessageType lastMessageType,
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
                conversation.getLastMessageType(),
                conversation.getUnreadCount(),
                conversation.getStatus(),
                conversation.isReplyWindowOpen()
        );
    }
}

