package com.ihh.wpBot.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(
        name = "conversations",
        indexes = {
                @Index(name = "idx_conversations_last_message_at", columnList = "last_message_at"),
                @Index(name = "uk_conversations_phone_number", columnList = "phone_number", unique = true)
        }
)
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "phone_number", nullable = false, length = 32, unique = true)
    private String phoneNumber;

    @Column(name = "contact_name", length = 128)
    private String contactName;

    @Column(name = "last_message_at", nullable = false)
    private LocalDateTime lastMessageAt;

    @Column(name = "last_inbound_at")
    private LocalDateTime lastInboundAt;

    @Column(name = "last_message_text", length = 500)
    private String lastMessageText;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_message_type", columnDefinition = "VARCHAR(32)", length = 32)
    private MessageType lastMessageType;

    @Column(name = "unread_count", nullable = false)
    private int unreadCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ConversationStatus status = ConversationStatus.OPEN;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Conversation() {
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (lastMessageAt == null) {
            lastMessageAt = createdAt;
        }
    }

    @Transient
    public boolean isReplyWindowOpen() {
        if (lastInboundAt == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        long hours = ChronoUnit.HOURS.between(lastInboundAt, now);
        return hours < 24;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public LocalDateTime getLastMessageAt() {
        return lastMessageAt;
    }

    public void setLastMessageAt(LocalDateTime lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }

    public LocalDateTime getLastInboundAt() {
        return lastInboundAt;
    }

    public void setLastInboundAt(LocalDateTime lastInboundAt) {
        this.lastInboundAt = lastInboundAt;
    }

    public String getLastMessageText() {
        return lastMessageText;
    }

    public void setLastMessageText(String lastMessageText) {
        this.lastMessageText = lastMessageText;
    }

    public MessageType getLastMessageType() {
        return lastMessageType;
    }

    public void setLastMessageType(MessageType lastMessageType) {
        this.lastMessageType = lastMessageType;
    }

    public int getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(int unreadCount) {
        this.unreadCount = unreadCount;
    }

    public ConversationStatus getStatus() {
        return status;
    }

    public void setStatus(ConversationStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

