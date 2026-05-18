package com.ihh.wpBot.controller.dto;

public class MetaTemplate {
    private String name;
    private String language;
    private String status;
    private String category;
    private String headerType;
    private String bodyText;

    public MetaTemplate() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getHeaderType() {
        return headerType;
    }

    public void setHeaderType(String headerType) {
        this.headerType = headerType;
    }

    public String getBodyText() {
        return bodyText;
    }

    public void setBodyText(String bodyText) {
        this.bodyText = bodyText;
    }
}
