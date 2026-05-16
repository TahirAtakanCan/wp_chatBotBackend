package com.ihh.wpBot.controller.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record ReplyMediaRequest(
        @JsonAlias({"imageUrl", "videoUrl", "url"}) String mediaUrl,
        String caption,
        Long sizeBytes
) {
}
