package com.ihh.wpBot.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
        String caption,
        String mediaId,
        @JsonProperty("media_id") String mediaIdSnakeCase,
        String mediaUrl,
        String url,
        String mimeType,
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
                message.getCaption(),
                message.getMediaId(),
                message.getMediaId(),
                message.getMediaUrl(),
                message.getMediaUrl(),
                message.getMimeType(),
                message.getWaMessageId(),
                message.getSentAt(),
                message.getStatus()
        );
    }
}

