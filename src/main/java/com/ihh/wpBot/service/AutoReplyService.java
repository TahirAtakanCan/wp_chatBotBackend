package com.ihh.wpBot.service;

import com.ihh.wpBot.model.AutoReply;
import com.ihh.wpBot.model.AutoReplySettings;
import com.ihh.wpBot.repository.AutoReplyRepository;
import com.ihh.wpBot.repository.AutoReplySettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AutoReplyService {

    private static final Logger log = LoggerFactory.getLogger(AutoReplyService.class);

    private final AutoReplyRepository autoReplyRepository;
    private final AutoReplySettingsRepository settingsRepository;
    private final WhatsAppService whatsAppService;

    /** Kişi başına son otomatik yanıt zamanı (cooldown, in-memory). */
    private final Map<String, LocalDateTime> lastReplyTime = new ConcurrentHashMap<>();

    public AutoReplyService(AutoReplyRepository autoReplyRepository,
                            AutoReplySettingsRepository settingsRepository,
                            WhatsAppService whatsAppService) {
        this.autoReplyRepository = autoReplyRepository;
        this.settingsRepository  = settingsRepository;
        this.whatsAppService     = whatsAppService;
    }

    /**
     * INBOUND text mesaj geldiğinde çağrılır.
     *
     * @param phoneNumber gönderen kişi
     * @param messageText mesaj içeriği
     * @return otomatik yanıt gönderildiyse true
     */
    public boolean processIncomingMessage(String phoneNumber, String messageText) {
        if (messageText == null || messageText.isBlank()) {
            return false;
        }

        AutoReplySettings settings = getOrCreateSettings();

        if (!Boolean.TRUE.equals(settings.getEnabled())) {
            log.debug("Auto-reply disabled globally, skipping");
            return false;
        }

        if (isInCooldown(phoneNumber, settings.getCooldownSeconds())) {
            log.debug("Phone {} in cooldown, skipping auto-reply", phoneNumber);
            return false;
        }

        if (Boolean.TRUE.equals(settings.getUseWorkingHours())
                && !isWithinWorkingHours(settings)) {
            sendReply(phoneNumber, settings.getOutOfHoursMessage(), "OUT_OF_HOURS");
            updateCooldown(phoneNumber);
            return true;
        }

        Optional<AutoReply> match = findMatchingReply(messageText);
        if (match.isEmpty()) {
            log.debug("No keyword match for incoming message (auto-reply phase 1 miss)");
            return false;
        }

        AutoReply reply = match.get();
        try {
            sendReply(phoneNumber, reply.getReplyText(), reply.getCategory());

            reply.setMatchCount(reply.getMatchCount() + 1);
            reply.setLastMatchedAt(LocalDateTime.now());
            autoReplyRepository.save(reply);

            updateCooldown(phoneNumber);
            log.info("Auto-reply sent to {} — category={}", phoneNumber, reply.getCategory());
            return true;
        } catch (Exception e) {
            log.error("Auto-reply send failed for {}", phoneNumber, e);
            return false;
        }
    }

    /**
     * Mesajı normalize ederek aktif anahtar kelimeleri sırayla dener.
     * Öncelik düşük sayı → yüksek öncelik (ORDER BY priority ASC).
     */
    public Optional<AutoReply> findMatchingReply(String messageText) {
        String normalized = normalize(messageText);
        List<AutoReply> active = autoReplyRepository.findByActiveTrueOrderByPriorityAsc();

        for (AutoReply reply : active) {
            for (String keyword : reply.getKeywords().split(",")) {
                String nk = normalize(keyword.trim());
                if (!nk.isEmpty() && normalized.contains(nk)) {
                    log.debug("Match: '{}' → keyword='{}' category={}", normalized, nk, reply.getCategory());
                    return Optional.of(reply);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Metni normalize eder:
     * - Küçük harf (Türkçe locale)
     * - Türkçe → ASCII karakter dönüşümü
     * - Noktalama kaldır
     * - Fazla boşluk → tek boşluk
     */
    public String normalize(String text) {
        if (text == null) return "";
        return text
                .toLowerCase(Locale.forLanguageTag("tr-TR"))
                .replace('ı', 'i').replace('İ', 'i')
                .replace('ş', 's').replace('Ş', 's')
                .replace('ğ', 'g').replace('Ğ', 'g')
                .replace('ü', 'u').replace('Ü', 'u')
                .replace('ö', 'o').replace('Ö', 'o')
                .replace('ç', 'c').replace('Ç', 'c')
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public AutoReplySettings getOrCreateSettings() {
        return settingsRepository.findById(1L).orElseGet(() -> {
            AutoReplySettings def = new AutoReplySettings();
            def.setId(1L);
            return settingsRepository.save(def);
        });
    }

    private boolean isInCooldown(String phoneNumber, Integer cooldownSeconds) {
        if (cooldownSeconds == null || cooldownSeconds <= 0) return false;
        LocalDateTime last = lastReplyTime.get(phoneNumber);
        if (last == null) return false;
        return Duration.between(last, LocalDateTime.now()).getSeconds() < cooldownSeconds;
    }

    private void updateCooldown(String phoneNumber) {
        lastReplyTime.put(phoneNumber, LocalDateTime.now());
        if (lastReplyTime.size() > 1000) {
            LocalDateTime cutoff = LocalDateTime.now().minusHours(1);
            lastReplyTime.entrySet().removeIf(e -> e.getValue().isBefore(cutoff));
        }
    }

    private boolean isWithinWorkingHours(AutoReplySettings settings) {
        LocalTime now   = LocalTime.now();
        LocalTime start = settings.getWorkingHoursStart();
        LocalTime end   = settings.getWorkingHoursEnd();
        return !now.isBefore(start) && !now.isAfter(end);
    }

    private void sendReply(String phoneNumber, String text, String category) {
        whatsAppService.sendTextMessage(phoneNumber, text);
        log.info("Auto-reply dispatched: phone={}, category={}", phoneNumber, category);
    }
}
