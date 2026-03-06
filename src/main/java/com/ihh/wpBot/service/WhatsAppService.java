package com.ihh.wpBot.service;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WhatsAppService {

    private static final String NODE_API_URL = "http://localhost:3000/send";
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_WAIT_MS = 15_000; // Node.js client yeniden başlarken bekleme süresi
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Node.js mikroservisine HTTP POST isteği atarak mesaj gönderir.
     * 503 (client yeniden başlıyor) veya detached frame hatası alırsa otomatik retry yapar.
     *
     * @param phoneNumber Hedef telefon numarası (örn: 905551234567)
     * @param message     Gönderilecek metin (opsiyonel, null olabilir)
     * @param mediaUrls   Medya URL listesi (opsiyonel, listedeki ilk URL kullanılır)
     */
    public void sendMessage(String phoneNumber, String message, List<String> mediaUrls) throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("number", phoneNumber);

        if (message != null && !message.isBlank()) {
            body.put("message", message);
        }

        if (mediaUrls != null && !mediaUrls.isEmpty()) {
            body.put("mediaUrl", mediaUrls.get(0));
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                restTemplate.postForEntity(NODE_API_URL, request, String.class);
                return; // Başarılı — çık
            } catch (HttpServerErrorException e) {
                lastException = e;
                String responseBody = e.getResponseBodyAsString();
                boolean isRetryable = e.getStatusCode().value() == 503
                        || responseBody.contains("detached Frame")
                        || responseBody.contains("not ready");

                if (isRetryable && attempt < MAX_RETRIES) {
                    Thread.sleep(RETRY_WAIT_MS);
                } else {
                    break;
                }
            } catch (Exception e) {
                lastException = e;
                // Bağlantı hatası (Node.js kapalı vs.) — retry
                if (attempt < MAX_RETRIES) {
                    Thread.sleep(RETRY_WAIT_MS);
                } else {
                    break;
                }
            }
        }

        throw new Exception("Node.js mikroservisine mesaj gönderilemedi (" + MAX_RETRIES + " deneme): "
                + lastException.getMessage(), lastException);
    }
}