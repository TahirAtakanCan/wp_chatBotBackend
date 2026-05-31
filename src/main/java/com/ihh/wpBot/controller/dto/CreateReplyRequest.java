package com.ihh.wpBot.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateReplyRequest(
        @NotBlank(message = "category boş olamaz") String category,
        @NotBlank(message = "keywords boş olamaz") String keywords,
        @NotBlank(message = "replyText boş olamaz") String replyText,
        Boolean active,
        Integer priority
) {}
