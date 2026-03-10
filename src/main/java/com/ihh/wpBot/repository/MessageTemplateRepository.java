package com.ihh.wpBot.repository;

import com.ihh.wpBot.model.MessageTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageTemplateRepository extends JpaRepository<MessageTemplate, Long> {
    List<MessageTemplate> findByCreatedBy(String username);
}
