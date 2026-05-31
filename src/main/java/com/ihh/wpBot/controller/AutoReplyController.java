package com.ihh.wpBot.controller;

import com.ihh.wpBot.controller.dto.CreateReplyRequest;
import com.ihh.wpBot.controller.dto.TestAutoReplyRequest;
import com.ihh.wpBot.model.AutoReply;
import com.ihh.wpBot.model.AutoReplySettings;
import com.ihh.wpBot.repository.AutoReplyRepository;
import com.ihh.wpBot.repository.AutoReplySettingsRepository;
import com.ihh.wpBot.service.AutoReplyService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auto-reply")
public class AutoReplyController {

    private final AutoReplyRepository repository;
    private final AutoReplySettingsRepository settingsRepository;
    private final AutoReplyService service;

    public AutoReplyController(AutoReplyRepository repository,
                               AutoReplySettingsRepository settingsRepository,
                               AutoReplyService service) {
        this.repository         = repository;
        this.settingsRepository = settingsRepository;
        this.service            = service;
    }

    // ─── CRUD ──────────────────────────────────────────────────────────────────

    @GetMapping("/replies")
    public ResponseEntity<List<AutoReply>> list() {
        return ResponseEntity.ok(repository.findAllByOrderByPriorityAscCreatedAtDesc());
    }

    @PostMapping("/replies")
    public ResponseEntity<?> create(@Valid @RequestBody CreateReplyRequest req) {
        if (repository.existsByCategory(req.category())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "DUPLICATE_CATEGORY",
                    "message", "Bu isimde bir kategori zaten var"
            ));
        }

        AutoReply reply = new AutoReply();
        reply.setCategory(req.category());
        reply.setKeywords(req.keywords());
        reply.setReplyText(req.replyText());
        reply.setActive(req.active() != null ? req.active() : true);
        reply.setPriority(req.priority() != null ? req.priority() : 100);

        return ResponseEntity.ok(repository.save(reply));
    }

    @PutMapping("/replies/{id}")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @Valid @RequestBody CreateReplyRequest req) {
        Optional<AutoReply> existing = repository.findById(id);
        if (existing.isEmpty()) return ResponseEntity.notFound().build();

        AutoReply reply = existing.get();
        reply.setCategory(req.category());
        reply.setKeywords(req.keywords());
        reply.setReplyText(req.replyText());
        if (req.active() != null) reply.setActive(req.active());
        if (req.priority() != null) reply.setPriority(req.priority());

        return ResponseEntity.ok(repository.save(reply));
    }

    @DeleteMapping("/replies/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!repository.existsById(id)) return ResponseEntity.notFound().build();
        repository.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    @PostMapping("/replies/{id}/toggle")
    public ResponseEntity<?> toggle(@PathVariable Long id) {
        Optional<AutoReply> existing = repository.findById(id);
        if (existing.isEmpty()) return ResponseEntity.notFound().build();

        AutoReply reply = existing.get();
        reply.setActive(!Boolean.TRUE.equals(reply.getActive()));
        return ResponseEntity.ok(repository.save(reply));
    }

    // ─── SETTINGS ──────────────────────────────────────────────────────────────

    @GetMapping("/settings")
    public ResponseEntity<AutoReplySettings> getSettings() {
        return ResponseEntity.ok(service.getOrCreateSettings());
    }

    @PutMapping("/settings")
    public ResponseEntity<?> updateSettings(@RequestBody AutoReplySettings settings) {
        settings.setId(1L);
        return ResponseEntity.ok(settingsRepository.save(settings));
    }

    // ─── TEST ──────────────────────────────────────────────────────────────────

    /**
     * Bir mesajın hangi cevabı tetikleyeceğini gösterir.
     * Frontend'deki "Test Et" butonu için.
     */
    @PostMapping("/test")
    public ResponseEntity<?> test(@Valid @RequestBody TestAutoReplyRequest req) {
        String normalized = service.normalize(req.message());

        return service.findMatchingReply(req.message())
                .map(reply -> {
                    String matchedKeyword = "";
                    for (String kw : reply.getKeywords().split(",")) {
                        String nk = service.normalize(kw.trim());
                        if (!nk.isEmpty() && normalized.contains(nk)) {
                            matchedKeyword = kw.trim();
                            break;
                        }
                    }
                    return ResponseEntity.ok((Object) Map.of(
                            "matched", true,
                            "category", reply.getCategory(),
                            "keyword", matchedKeyword,
                            "replyText", reply.getReplyText()
                    ));
                })
                .orElse(ResponseEntity.ok(Map.of(
                        "matched", false,
                        "normalized", normalized,
                        "message", "Hiçbir anahtar kelime eşleşmedi"
                )));
    }
}
