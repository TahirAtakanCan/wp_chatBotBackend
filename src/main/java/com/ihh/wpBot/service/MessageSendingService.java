package com.ihh.wpBot.service;

import com.ihh.wpBot.model.SendSession;
import com.ihh.wpBot.model.SendStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MessageSendingService {

    private static final Logger log = LoggerFactory.getLogger(MessageSendingService.class);

    private final Map<String, SendSession> activeSessions = new ConcurrentHashMap<>();
    private final WhatsAppService whatsAppService;

    @Value("${app.public.url}")
    private String publicUrl;

    @Autowired
    public MessageSendingService(WhatsAppService whatsAppService) {
        this.whatsAppService = whatsAppService;
    }

    public SendSession createSession(int totalNumbers) {
        SendSession session = new SendSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setTotalNumbers(totalNumbers);
        session.setStatus(SendStatus.IDLE);
        activeSessions.put(session.getSessionId(), session);
        return session;
    }

    public SendSession getSession(String sessionId) {
        return activeSessions.getOrDefault(sessionId, new SendSession());
    }

    public SendSession getActiveSession(String sessionId) {
        return activeSessions.get(sessionId);
    }

    public void stopSession(String sessionId) {
        SendSession session = activeSessions.get(sessionId);
        if (session != null && session.getStatus() == SendStatus.SENDING) {
            session.setStatus(SendStatus.PAUSED);
            session.addLog(getFormattedTime() + " [SİSTEM] Gönderim durduruldu.");
        }
    }

    @Async
    public void startSendingProcess(
            String sessionId,
            String whatsappSessionId,
            List<String> phoneNumbers,
            String templateName,
            int minDelay,
            int maxDelay,
            List<String> mediaPaths,
            List<String> personalizedMessages,
            boolean isPersonalized
    ) {
        SendSession session = activeSessions.get(sessionId);
        if (session == null) return;

        session.setTotalNumbers(phoneNumbers.size());
        session.setStatus(SendStatus.SENDING);
        session.addLog(getFormattedTime() + " [SİSTEM] Meta API'ye bağlanılıyor...");

        runSendingLoop(session, phoneNumbers, templateName, mediaPaths, personalizedMessages);
    }

    @Async
    public void resumeSession(
            String sessionId,
            String whatsappSessionId,
            List<String> phoneNumbers,
            String templateName,
            List<String> mediaPaths,
            boolean isPersonalized
    ) {
        SendSession session = activeSessions.get(sessionId);
        if (session == null) {
            throw new IllegalStateException("Session bulunamadı. Uygulama yeniden başladıysa oturum bellekte yoktur.");
        }
        if (session.getStatus() != SendStatus.RATE_LIMITED) {
            throw new IllegalStateException("Session sadece RATE_LIMITED durumunda devam ettirilebilir.");
        }
        if (phoneNumbers == null || phoneNumbers.isEmpty()) {
            throw new IllegalArgumentException("phoneNumbers boş olamaz.");
        }

        session.setTotalNumbers(phoneNumbers.size());
        session.setStatus(SendStatus.SENDING);
        session.addLog(getFormattedTime()
                + " [SİSTEM] RATE_LIMITED oturumu kaldığı yerden devam ettiriliyor...");

        runSendingLoop(session, phoneNumbers, templateName, mediaPaths, null);
    }

    private void runSendingLoop(
            SendSession session,
            List<String> phoneNumbers,
            String templateName,
            List<String> mediaPaths,
            List<String> personalizedMessages
    ) {

        try {
            for (int i = session.getSentCount(); i < phoneNumbers.size(); i++) {
                if (session.getStatus() == SendStatus.PAUSED) break;

                String phone = phoneNumbers.get(i);
                session.setCurrentNumber(phone);
                boolean shouldIncrement = true;
                List<String> bodyParameters = buildBodyParameters(templateName, personalizedMessages, i);

                try {
                    if (mediaPaths != null && !mediaPaths.isEmpty()) {
                        String filename = extractFilename(mediaPaths.get(0));
                        String base = publicUrl.endsWith("/")
                                ? publicUrl.substring(0, publicUrl.length() - 1)
                                : publicUrl;
                        String imageUrl = base + "/api/media/" + filename;
                        whatsAppService.sendImageTemplateMessage(
                                phone, templateName, "tr", imageUrl, bodyParameters);
                    } else {
                        whatsAppService.sendTemplateMessage(phone, templateName, "tr", bodyParameters);
                    }
                    session.addLog(getFormattedTime() + " [GÖNDER] Mesaj Meta API'ye iletildi. ✔");
                } catch (Exception e) {
                    String errorMessage = e.getMessage() != null ? e.getMessage() : "Bilinmeyen hata";
                    if (errorMessage.contains("HTTP 429")) {
                        session.setStatus(SendStatus.RATE_LIMITED);
                        session.addLog(getFormattedTime() + " [RATE LIMIT] Meta API 429 döndü. Gönderim durduruldu.");
                        shouldIncrement = false;
                        break;
                    }
                    session.addLog(getFormattedTime() + " [HATA] " + phone + " - " + errorMessage);
                } finally {
                    if (shouldIncrement) {
                        session.setSentCount(session.getSentCount() + 1);
                        session.setProgress(
                                (double) session.getSentCount() / session.getTotalNumbers());
                    }
                }
            }

            if (session.getStatus() != SendStatus.PAUSED
                    && session.getStatus() != SendStatus.RATE_LIMITED) {
                session.setStatus(SendStatus.COMPLETED);
                session.setProgress(1.0);
                session.addLog(getFormattedTime()
                        + " [SİSTEM] Tüm gönderimler tamamlandı.");
            }

        } catch (Exception e) {
            session.setStatus(SendStatus.FAILED);
            session.addLog(getFormattedTime()
                    + " [KRİTİK HATA] Gönderim çöktü: " + e.getMessage());
        }
    }

    private String extractFilename(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isBlank()) {
            return "";
        }
        String s = pathOrUrl.trim();
        int q = s.indexOf('?');
        if (q >= 0) {
            s = s.substring(0, q);
        }
        int apiMedia = s.indexOf("/api/media/");
        if (apiMedia >= 0) {
            return s.substring(apiMedia + "/api/media/".length());
        }
        int lastSlash = s.lastIndexOf('/');
        return lastSlash >= 0 ? s.substring(lastSlash + 1) : s;
    }

    private List<String> buildBodyParameters(String templateName,
                                             List<String> personalizedMessages,
                                             int index) {
        Map<String, List<String>> templateDefaults = Map.of(
                "bagis_tesekkur", List.of("Ahmet Yılmaz", "500"),
                "kurban_kardeslik_cagri", List.of(),
                "test_basit", List.of()
        );

        List<String> params = List.of();
        if (personalizedMessages != null && !personalizedMessages.isEmpty() && index < personalizedMessages.size()) {
            String msg = personalizedMessages.get(index);
            if (msg != null && !msg.isBlank()) {
                params = List.of(msg);
            }
        }

        if (params.isEmpty() && templateDefaults.containsKey(templateName)) {
            params = templateDefaults.get(templateName);
        }

        log.info("Building body params for template={}, params={}", templateName, params);
        return params;
    }

    private String getFormattedTime() {
        return "[" + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "]";
    }
}