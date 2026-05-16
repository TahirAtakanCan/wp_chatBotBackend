package com.ihh.wpBot.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record ReplyDocumentRequest(
        @NotBlank(message = "mediaUrl boş olamaz") String mediaUrl,
        @NotBlank(message = "filename boş olamaz") String filename,
        String caption,
        Long sizeBytes
) {
}
