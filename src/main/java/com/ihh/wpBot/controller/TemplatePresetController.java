package com.ihh.wpBot.controller;

import com.ihh.wpBot.controller.dto.CreatePresetRequest;
import com.ihh.wpBot.controller.dto.MetaTemplate;
import com.ihh.wpBot.model.TemplatePreset;
import com.ihh.wpBot.repository.TemplatePresetRepository;
import com.ihh.wpBot.service.MetaTemplateService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/templates")
public class TemplatePresetController {

    private final MetaTemplateService metaTemplateService;
    private final TemplatePresetRepository presetRepository;

    public TemplatePresetController(MetaTemplateService metaTemplateService, TemplatePresetRepository presetRepository) {
        this.metaTemplateService = metaTemplateService;
        this.presetRepository = presetRepository;
    }

    @GetMapping("/meta")
    public ResponseEntity<List<MetaTemplate>> listMetaTemplates() {
        return ResponseEntity.ok(metaTemplateService.fetchAllTemplates());
    }

    @PostMapping("/meta/refresh")
    public ResponseEntity<Map<String, Object>> refreshMetaTemplates() {
        metaTemplateService.refreshCache();
        List<MetaTemplate> fresh = metaTemplateService.fetchAllTemplates();
        return ResponseEntity.ok(Map.of(
                "refreshed", true,
                "count", fresh.size(),
                "templates", fresh
        ));
    }

    @GetMapping("/presets")
    public ResponseEntity<List<TemplatePreset>> listPresets() {
        return ResponseEntity.ok(presetRepository.findAllByOrderByCreatedAtDesc());
    }

    @GetMapping("/presets/by-template")
    public ResponseEntity<List<TemplatePreset>> presetsByTemplate(@RequestParam("name") String metaTemplateName) {
        return ResponseEntity.ok(presetRepository.findByMetaTemplateNameOrderByCreatedAtDesc(metaTemplateName));
    }

    @PostMapping("/presets")
    public ResponseEntity<?> createPreset(@Valid @RequestBody CreatePresetRequest request) {
        Optional<MetaTemplate> metaTemplate = metaTemplateService.findByName(request.metaTemplateName());
        if (metaTemplate.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "META_TEMPLATE_NOT_FOUND",
                    "message", "Meta'da '" + request.metaTemplateName() + "' adlı bir template bulunamadı"
            ));
        }

        String expectedHeader = normalizeMediaType(metaTemplate.get().getHeaderType());
        String requestedMediaType = normalizeMediaType(request.mediaType());
        if (requestedMediaType != null && expectedHeader != null && !expectedHeader.equals(requestedMediaType)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "MEDIA_TYPE_MISMATCH",
                    "message", String.format(
                            "Template '%s' header tipi %s, ama %s medya yüklediniz. Uyumsuz.",
                            request.metaTemplateName(), expectedHeader, requestedMediaType
                    )
            ));
        }

        if (presetRepository.existsByDisplayName(request.displayName())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "DUPLICATE_DISPLAY_NAME",
                    "message", "Bu isimde bir hazır kayıt zaten var"
            ));
        }

        TemplatePreset preset = new TemplatePreset();
        preset.setDisplayName(request.displayName());
        preset.setMetaTemplateName(request.metaTemplateName());
        preset.setLanguage(request.language() != null && !request.language().isBlank() ? request.language() : "tr");
        preset.setMediaType(requestedMediaType);
        preset.setMediaUrl(request.mediaUrl());
        preset.setMediaFilename(request.mediaFilename());
        preset.setMediaSizeBytes(request.mediaSizeBytes());
        preset.setMimeType(request.mimeType());

        TemplatePreset saved = presetRepository.save(preset);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/presets/{id}")
    public ResponseEntity<?> updatePreset(@PathVariable Long id, @Valid @RequestBody CreatePresetRequest request) {
        Optional<TemplatePreset> existing = presetRepository.findById(id);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        TemplatePreset preset = existing.get();
        preset.setDisplayName(request.displayName());
        preset.setMediaUrl(request.mediaUrl());
        preset.setMediaFilename(request.mediaFilename());
        preset.setMediaSizeBytes(request.mediaSizeBytes());
        preset.setMimeType(request.mimeType());
        preset.setMediaType(normalizeMediaType(request.mediaType()));

        return ResponseEntity.ok(presetRepository.save(preset));
    }

    @DeleteMapping("/presets/{id}")
    public ResponseEntity<?> deletePreset(@PathVariable Long id) {
        if (!presetRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        presetRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    @GetMapping("/presets/{id}")
    public ResponseEntity<TemplatePreset> getPreset(@PathVariable Long id) {
        return presetRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private String normalizeMediaType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return null;
        }
        String normalized = rawType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "IMAGE", "VIDEO", "DOCUMENT" -> normalized;
            case "NONE", "TEXT" -> null;
            default -> normalized;
        };
    }
}
