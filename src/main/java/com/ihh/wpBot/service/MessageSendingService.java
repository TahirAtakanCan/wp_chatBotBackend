package com.ihh.wpBot.service;

import com.ihh.wpBot.model.Contact;
import com.ihh.wpBot.model.Conversation;
import com.ihh.wpBot.model.SendSession;
import com.ihh.wpBot.model.SendStatus;
import com.ihh.wpBot.repository.ContactRepository;
import com.ihh.wpBot.repository.ConversationRepository;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MessageSendingService {

    private static final Logger log = LoggerFactory.getLogger(MessageSendingService.class);

    private final Map<String, SendSession> activeSessions = new ConcurrentHashMap<>();
    private final WhatsAppService whatsAppService;
    private final ContactRepository contactRepository;
    private final ConversationRepository conversationRepository;

    @Value("${app.public.url}")
    private String publicUrl;

    @Autowired
    public MessageSendingService(
            WhatsAppService whatsAppService,
            ContactRepository contactRepository,
            ConversationRepository conversationRepository
    ) {
        this.whatsAppService = whatsAppService;
        this.contactRepository = contactRepository;
        this.conversationRepository = conversationRepository;
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
                List<String> bodyParameters = buildBodyParameters(templateName, personalizedMessages, i, phone);

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

                    // Otomatik vCard gönderimi geçici olarak devre dışı bırakıldı.
                    // WhatsApp 24 saat penceresi nedeniyle template sonrası vCard
                    // ilk gönderim için geçmiyor; vCard artık Inbox üzerinden manuel
                    // olarak gönderiliyor.
                    // sendContactCardSafely(session, phone);
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

    private void sendContactCardSafely(SendSession session, String phone) {
        try {
            Thread.sleep(5000);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Contact card thread interrupted for {}", phone);
            return;
        }

        try {
            String contactWaId = whatsAppService.sendContactCard(phone);
            log.info("Contact card sent to {}, waMessageId={}", phone, contactWaId);
            session.addLog(getFormattedTime() + " [KART] Kişi kartı gönderildi: " + phone);
        } catch (Exception ex) {
            String msg = ex.getMessage() != null ? ex.getMessage() : "Bilinmeyen hata";
            log.warn("Contact card failed for {}: {}", phone, msg);
            session.addLog(getFormattedTime() + " [KART HATASI] " + phone + ": " + msg);
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
                                             int index,
                                             String phoneNumber) {
        if (personalizedMessages != null && !personalizedMessages.isEmpty() && index < personalizedMessages.size()) {
            String msg = personalizedMessages.get(index);
            if (msg != null && !msg.isBlank()) {
                List<String> params = List.of(msg);
                log.info("Building body params for template={}, phone={}, params={} (personalized)",
                        templateName, phoneNumber, params);
                return params;
            }
        }

        List<String> params = switch (templateName) {
            case "kurban_kardeslik_cagri_v2" -> List.of(resolveContactName(phoneNumber));
            case "bagis_tesekkur" -> List.of("Ahmet Yılmaz", "500");
            case "kurban_kardeslik_cagri", "test_basit" -> List.of();
            default -> List.of();
        };

        log.info("Building body params for template={}, phone={}, params={}",
                templateName, phoneNumber, params);
        return params;
    }

    private String resolveContactName(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            log.info("resolveContactName called with blank phone, using fallback");
            return "Değerli Bağışçımız";
        }

        String trimmed = phoneNumber.trim();
        String withoutPlus = trimmed.startsWith("+") ? trimmed.substring(1) : trimmed;
        String withPlus = trimmed.startsWith("+") ? trimmed : "+" + trimmed;

        try {
            Optional<Contact> contactOpt = contactRepository.findByPhone(withoutPlus);
            if (contactOpt.isEmpty()) {
                contactOpt = contactRepository.findByPhone(withPlus);
            }
            if (contactOpt.isPresent()) {
                String name = contactOpt.get().getName();
                if (name != null && !name.isBlank()) {
                    log.info("Contact name from CONTACT for phone={}: {}", phoneNumber, name);
                    return name.trim();
                }
            }
        } catch (Exception e) {
            log.warn("Error reading from CONTACT for phone={}: {}", phoneNumber, e.getMessage());
        }

        try {
            Optional<Conversation> convOpt = conversationRepository.findByPhoneNumber(withPlus);
            if (convOpt.isPresent()) {
                String name = convOpt.get().getContactName();
                if (name != null && !name.isBlank()) {
                    log.info("Contact name from Conversation for phone={}: {}", phoneNumber, name);
                    return name.trim();
                }
            }
        } catch (Exception e) {
            log.warn("Error reading from Conversation for phone={}: {}", phoneNumber, e.getMessage());
        }

        log.info("No contact name found for phone={}, using fallback", phoneNumber);
        return "Değerli Bağışçımız";
    }

    private String getFormattedTime() {
        return "[" + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "]";
    }
}