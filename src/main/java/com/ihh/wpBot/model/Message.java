package com.ihh.wpBot.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "messages",
        indexes = {
                @Index(name = "idx_messages_conversation_id", columnList = "conversation_id"),
                @Index(name = "idx_messages_wa_message_id", columnList = "wa_message_id"),
                @Index(name = "idx_messages_sent_at", columnList = "sent_at")
        }
)
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 32)
    private MessageDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 32)
    private MessageType messageType;

    @Column(name = "content", length = 4000)
    private String content;

    @Column(name = "caption", length = 4000)
    private String caption;

    @Column(name = "media_id", length = 128)
    private String mediaId;

    @Column(name = "media_url", length = 512)
    private String mediaUrl;

    @Column(name = "mime_type", length = 128)
    private String mimeType;

    @Column(name = "media_storage_path", length = 1024)
    private String mediaStoragePath;

    @Column(name = "wa_message_id", length = 128)
    private String waMessageId;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private MessageStatus status = MessageStatus.PENDING;

    public Message() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public void setConversation(Conversation conversation) {
        this.conversation = conversation;
    }

    public MessageDirection getDirection() {
        return direction;
    }

    public void setDirection(MessageDirection direction) {
        this.direction = direction;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getWaMessageId() {
        return waMessageId;
    }

    public void setWaMessageId(String waMessageId) {
        this.waMessageId = waMessageId;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public String getMediaId() {
        return mediaId;
    }

    public void setMediaId(String mediaId) {
        this.mediaId = mediaId;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public void setMediaUrl(String mediaUrl) {
        this.mediaUrl = mediaUrl;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getMediaStoragePath() {
        return mediaStoragePath;
    }

    public void setMediaStoragePath(String mediaStoragePath) {
        this.mediaStoragePath = mediaStoragePath;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public MessageStatus getStatus() {
        return status;
    }

    public void setStatus(MessageStatus status) {
        this.status = status;
    }
}

