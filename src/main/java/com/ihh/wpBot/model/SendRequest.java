package com.ihh.wpBot.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class SendRequest {
    private String sessionId;
    private List<String> phoneNumbers;
    private String message;
    private List<String> personalizedMessages;
    private String imageUrl;
    private String mediaUrl;
    private BulkMediaType mediaType;
    private String filename;

    @JsonProperty("isPersonalized")  // Jackson'a "isPersonalized" olarak oku de
    private boolean personalized;    // field adı artık "personalized" — Lombok setPersonalized() üretir

    private int minDelay;
    private int maxDelay;
    private List<MediaRequest> media;
}