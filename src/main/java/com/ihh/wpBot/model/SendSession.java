package com.ihh.wpBot.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class SendSession {
    private String sessionId;
    private List<String> phoneNumbers = new ArrayList<>();
    private String templateName;
    private String language = "tr";
    private String mediaUrl;
    private BulkMediaType mediaType;
    private String filename;
    private boolean personalized;
    private List<String> personalizedMessages = new ArrayList<>();
    private SendStatus status = SendStatus.IDLE;
    private int totalNumbers;
    private int sentCount = 0;
    private double progress = 0.0;
    private String currentNumber;
    private List<String> logs = new ArrayList<>();

    // Multi-thread çalışırken logların sırasının karışmaması için synchronized yapıyoruz
    public synchronized void addLog(String log) {
        this.logs.add(log);
    }
}