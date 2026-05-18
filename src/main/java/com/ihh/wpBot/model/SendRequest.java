package com.ihh.wpBot.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class SendRequest {
    private String templateName;
    @JsonProperty("template")
    private String templateAlias;
    private String language;
    private String templateLanguage;

    private String sessionId;
    private List<String> phoneNumbers;
    private String message;
    @JsonAlias("messages")
    private List<String> personalizedMessages;
    private String imageUrl;
    private String mediaUrl;
    private BulkMediaType mediaType;
    private String filename;

    @JsonProperty("isPersonalized")  // Jackson'a "isPersonalized" olarak oku de
    private boolean personalized;    // field adı artık "personalized" — Lombok setPersonalized() üretir

    private Integer minDelay;
    private Integer maxDelay;
    private List<MediaRequest> media;

    public String getResolvedTemplateName() {
        if (templateName != null && !templateName.isBlank()) {
            return templateName.trim();
        }
        if (templateAlias != null && !templateAlias.isBlank()) {
            return templateAlias.trim();
        }
        if (message != null && !message.isBlank()) {
            return message.trim();
        }
        return null;
    }

    public String getResolvedLanguage() {
        if (language != null && !language.isBlank()) {
            return language.trim();
        }
        if (templateLanguage != null && !templateLanguage.isBlank()) {
            return templateLanguage.trim();
        }
        return "tr";
    }

    public String getResolvedMediaUrl() {
        if (mediaUrl != null && !mediaUrl.isBlank()) {
            return mediaUrl.trim();
        }
        if (imageUrl != null && !imageUrl.isBlank()) {
            return imageUrl.trim();
        }
        return null;
    }
}