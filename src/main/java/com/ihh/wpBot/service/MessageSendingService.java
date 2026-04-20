package com.ihh.wpBot.service;

import com.ihh.wpBot.model.SendSession;
import com.ihh.wpBot.model.SendStatus;
import org.springframework.beans.factory.annotation.Autowired;
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

    private final Map<String, SendSession> activeSessions = new ConcurrentHashMap<>();
    private final WhatsAppService whatsAppService;

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

        runSendingLoop(session, phoneNumbers, templateName, mediaPaths);
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

        runSendingLoop(session, phoneNumbers, templateName, mediaPaths);
    }

    private void runSendingLoop(
            SendSession session,
            List<String> phoneNumbers,
            String templateName,
            List<String> mediaPaths
    ) {

        try {
            for (int i = session.getSentCount(); i < phoneNumbers.size(); i++) {
                if (session.getStatus() == SendStatus.PAUSED) break;

                String phone = phoneNumbers.get(i);
                session.setCurrentNumber(phone);
                boolean shouldIncrement = true;

                try {
                    if (mediaPaths != null && !mediaPaths.isEmpty()) {
                        // Template header image kullanan Meta mesajı
                        whatsAppService.sendImageTemplateMessage(
                                phone, templateName, "tr", mediaPaths.get(0));
                    } else {
                        whatsAppService.sendTemplateMessage(phone, templateName, "tr");
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

    private String getFormattedTime() {
        return "[" + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "]";
    }
}