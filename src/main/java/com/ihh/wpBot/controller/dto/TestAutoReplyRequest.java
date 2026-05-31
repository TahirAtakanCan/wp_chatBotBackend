package com.ihh.wpBot.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record TestAutoReplyRequest(
        @NotBlank(message = "message boş olamaz") String message
) {}
