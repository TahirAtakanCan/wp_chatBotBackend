package com.ihh.wpBot.service;

import com.ihh.wpBot.model.SendSession;
import com.ihh.wpBot.model.SendStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MessageSendingService {

    private final Map<String, SendSession> activeSessions = new ConcurrentHashMap<>();
    private final WhatsAppService whatsAppService;
    private final Random random = new Random();

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

    public void stopSession(String sessionId) {
        SendSession session = activeSessions.get(sessionId);
        if (session != null && session.getStatus() == SendStatus.SENDING) {
            session.setStatus(SendStatus.PAUSED);
            session.addLog(getFormattedTime() + " [SİSTEM] Gönderim durduruldu.");
        }
    }

    @Async
    public void startSendingProcess(String sessionId, List<String> phoneNumbers, String message, 
                                    int minDelay, int maxDelay, List<String> mediaPaths) {
        
        SendSession session = activeSessions.get(sessionId);
        if (session == null) return;

        session.setStatus(SendStatus.SENDING);
        session.addLog(getFormattedTime() + " [SİSTEM] Node.js mikroservisine bağlanılıyor...");
        
        try {

            for (int i = session.getSentCount(); i < phoneNumbers.size(); i++) {
                if (session.getStatus() == SendStatus.PAUSED) {
                    break;
                }

                String phone = phoneNumbers.get(i);
                session.setCurrentNumber(phone);

                try {
                    // Artık mediaPaths'i de gönderiyoruz
                    whatsAppService.sendMessage(phone, message, mediaPaths); 
                    String medyaLog = mediaPaths.isEmpty() ? "" : " (Medya ile)";
                    session.addLog(getFormattedTime() + " [GÖNDER] " + phone + " numarasına gönderildi" + medyaLog + ". ✔");
                } catch (Exception e) {
                    session.addLog(getFormattedTime() + " [HATA] " + phone + " - " + e.getMessage());
                } finally {
                    session.setSentCount(session.getSentCount() + 1);
                    session.setProgress((double) session.getSentCount() / session.getTotalNumbers());
                }

                // Son numara değilse ve durdurulmadıysa rastgele süre bekle
                if (i < phoneNumbers.size() - 1 && session.getStatus() != SendStatus.PAUSED) {
                    int delaySeconds = random.nextInt((maxDelay - minDelay) + 1) + minDelay;
                    session.addLog(getFormattedTime() + " [BEKLE] " + delaySeconds + " saniye bekleniyor...");
                    Thread.sleep(delaySeconds * 1000L);
                }
            }
            
            if (session.getStatus() != SendStatus.PAUSED) {
                session.setStatus(SendStatus.COMPLETED);
                session.addLog(getFormattedTime() + " [SİSTEM] Tüm gönderimler tamamlandı.");
            }

        } catch (Exception e) {
            session.setStatus(SendStatus.FAILED);
            session.addLog(getFormattedTime() + " [KRİTİK HATA] Gönderim çöktü: " + e.getMessage());
        }
    }

    private String getFormattedTime() {
        return "[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "]";
    }
}