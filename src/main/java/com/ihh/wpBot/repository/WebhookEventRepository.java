package com.ihh.wpBot.repository;

import com.ihh.wpBot.model.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {
}
