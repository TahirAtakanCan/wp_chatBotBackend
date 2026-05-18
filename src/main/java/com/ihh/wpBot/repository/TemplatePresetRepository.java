package com.ihh.wpBot.repository;

import com.ihh.wpBot.model.TemplatePreset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TemplatePresetRepository extends JpaRepository<TemplatePreset, Long> {

    List<TemplatePreset> findAllByOrderByCreatedAtDesc();

    List<TemplatePreset> findByMetaTemplateNameOrderByCreatedAtDesc(String metaTemplateName);

    boolean existsByDisplayName(String displayName);
}
