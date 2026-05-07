package com.ihh.wpBot.controller.dto;

import com.ihh.wpBot.model.Message;
import com.ihh.wpBot.model.MessageDirection;
import com.ihh.wpBot.model.MessageStatus;
import com.ihh.wpBot.model.MessageType;

import java.time.LocalDateTime;

public record MessageDto(
        Long id,
        MessageDirection direction,
        MessageType messageType,
        String content,
        String waMessageId,
        LocalDateTime sentAt,
        MessageStatus status
) {
    public static MessageDto from(Message message) {
        return new MessageDto(
                message.getId(),
                message.getDirection(),
                message.getMessageType(),
                message.getContent(),
                message.getWaMessageId(),
                message.getSentAt(),
                message.getStatus()
        );
    }
}

