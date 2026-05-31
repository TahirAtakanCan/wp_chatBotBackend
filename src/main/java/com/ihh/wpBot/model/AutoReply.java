package com.ihh.wpBot.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "auto_reply")
public class AutoReply {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(nullable = false, length = 1000)
    private String keywords;

    @Column(nullable = false, length = 4000)
    private String replyText;

    @Column(nullable = false)
    private Boolean active = true;

    /** Düşük sayı = yüksek öncelik. Birden fazla eşleşme varsa en düşük kazanır. */
    @Column(nullable = false)
    private Integer priority = 100;

    @Column(nullable = false)
    private Long matchCount = 0L;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime lastMatchedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (active == null) active = true;
        if (priority == null) priority = 100;
        if (matchCount == null) matchCount = 0L;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getKeywords() { return keywords; }
    public void setKeywords(String keywords) { this.keywords = keywords; }

    public String getReplyText() { return replyText; }
    public void setReplyText(String replyText) { this.replyText = replyText; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }

    public Long getMatchCount() { return matchCount; }
    public void setMatchCount(Long matchCount) { this.matchCount = matchCount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getLastMatchedAt() { return lastMatchedAt; }
    public void setLastMatchedAt(LocalDateTime lastMatchedAt) { this.lastMatchedAt = lastMatchedAt; }
}
