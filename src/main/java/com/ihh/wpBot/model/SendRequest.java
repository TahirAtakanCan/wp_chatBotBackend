package com.ihh.wpBot.model;

import lombok.Data;
import java.util.List;

@Data
public class SendRequest {
    private List<String> phoneNumbers;
    private String message;
    private int minDelay;
    private int maxDelay;
    private List<MediaRequest> media;
}
