package com.ihh.wpBot.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record CreatePresetRequest(
        @NotBlank(message = "displayName boş olamaz") String displayName,
        @NotBlank(message = "metaTemplateName boş olamaz") String metaTemplateName,
        String language,
        String mediaType,
        String mediaUrl,
        String mediaFilename,
        Long mediaSizeBytes,
        String mimeType
) {
}
