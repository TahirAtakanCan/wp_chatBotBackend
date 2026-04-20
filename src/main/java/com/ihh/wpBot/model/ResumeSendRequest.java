package com.ihh.wpBot.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class ResumeSendRequest {
    private String whatsappSessionId;
    private List<String> phoneNumbers;
    private String templateName;
    private List<String> mediaPaths;

    @JsonProperty("isPersonalized")
    private boolean personalized;
}
