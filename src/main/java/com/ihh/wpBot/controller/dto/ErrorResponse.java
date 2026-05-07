package com.ihh.wpBot.controller.dto;

public record ErrorResponse(String error, String message) {
    public static ErrorResponse of(String error) {
        return new ErrorResponse(error, null);
    }
}

