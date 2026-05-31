package com.ihh.wpBot.repository;

import com.ihh.wpBot.model.AutoReplySettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutoReplySettingsRepository extends JpaRepository<AutoReplySettings, Long> {
}
