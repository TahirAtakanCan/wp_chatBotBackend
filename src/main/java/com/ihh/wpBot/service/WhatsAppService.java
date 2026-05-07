package com.ihh.wpBot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class WhatsAppService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppService.class);

    @Value("${meta.phone.id}")
    private String phoneId;

    @Value("${meta.access.token}")
    private String accessToken;

    private final RestTemplate restTemplate;

    public WhatsAppService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String sendTextMessage(String toPhoneNumber, String text) {
        String metaPhone = toPhoneNumber == null ? null : toPhoneNumber.trim();
        if (metaPhone != null && metaPhone.startsWith("+")) {
            metaPhone = metaPhone.substring(1);
        }

        log.info("Sending text message to {}", metaPhone);

        Map<String, Object> body = new HashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("to", metaPhone);
        body.put("type", "text");

        Map<String, Object> textBody = new HashMap<>();
        textBody.put("body", text);
        body.put("text", textBody);

        Map<String, Object> response;
        try {
            response = postMessageRequest(body);
        } catch (IllegalStateException e) {
            Throwable cause = e.getCause();
            if (cause instanceof HttpStatusCodeException httpEx && httpEx.getStatusCode().value() == 429) {
                throw new IllegalStateException("RATE_LIMITED", e);
            }
            throw e;
        }

        String waMessageId = extractWaMessageId(response);
        log.info("Text message sent, waMessageId={}", waMessageId);
        return waMessageId;
    }

    public void sendTemplateMessage(String toPhoneNumber, String templateName, String languageCode) {
        sendTemplateMessage(toPhoneNumber, templateName, languageCode, null);
    }

    public void sendTemplateMessage(String toPhoneNumber, String templateName, String languageCode,
                                    List<String> bodyParameters) {
        Map<String, Object> body = new HashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("to", toPhoneNumber);
        body.put("type", "template");

        Map<String, Object> templateMap = new HashMap<>();
        templateMap.put("name", templateName);

        Map<String, Object> languageMap = new HashMap<>();
        languageMap.put("code", languageCode);

        templateMap.put("language", languageMap);

        if (bodyParameters != null && !bodyParameters.isEmpty()) {
            List<Map<String, Object>> bodyParams = new ArrayList<>();
            for (String parameter : bodyParameters) {
                Map<String, Object> textParameter = new HashMap<>();
                textParameter.put("type", "text");
                textParameter.put("text", parameter);
                bodyParams.add(textParameter);
            }

            Map<String, Object> bodyComponent = new HashMap<>();
            bodyComponent.put("type", "body");
            bodyComponent.put("parameters", bodyParams);

            List<Map<String, Object>> components = new ArrayList<>();
            components.add(bodyComponent);
            templateMap.put("components", components);
        }

        body.put("template", templateMap);

        Map<String, Object> response = postMessageRequest(body);
        System.out.println("Meta API Response: " + response);
    }

    public void sendImageTemplateMessage(String toPhoneNumber, String templateName,
                                         String languageCode, String imageUrl) {
        sendImageTemplateMessage(toPhoneNumber, templateName, languageCode, imageUrl, null);
    }

    public void sendImageTemplateMessage(String toPhoneNumber, String templateName,
                                         String languageCode, String imageUrl,
                                         List<String> bodyParameters) {
        Map<String, Object> body = new HashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("to", toPhoneNumber);
        body.put("type", "template");

        Map<String, Object> templateMap = new HashMap<>();
        templateMap.put("name", templateName);

        Map<String, Object> languageMap = new HashMap<>();
        languageMap.put("code", languageCode);
        templateMap.put("language", languageMap);

        Map<String, Object> imageObj = new HashMap<>();
        imageObj.put("link", imageUrl);

        Map<String, Object> headerParameter = new HashMap<>();
        headerParameter.put("type", "image");
        headerParameter.put("image", imageObj);

        List<Map<String, Object>> headerParameters = new ArrayList<>();
        headerParameters.add(headerParameter);

        Map<String, Object> headerComponent = new HashMap<>();
        headerComponent.put("type", "header");
        headerComponent.put("parameters", headerParameters);

        ArrayList<Map<String, Object>> components = new ArrayList<>();
        components.add(headerComponent);

        if (bodyParameters != null && !bodyParameters.isEmpty()) {
            List<Map<String, Object>> bodyParams = new ArrayList<>();
            for (String parameter : bodyParameters) {
                Map<String, Object> textParameter = new HashMap<>();
                textParameter.put("type", "text");
                textParameter.put("text", parameter);
                bodyParams.add(textParameter);
            }

            Map<String, Object> bodyComponent = new HashMap<>();
            bodyComponent.put("type", "body");
            bodyComponent.put("parameters", bodyParams);
            components.add(bodyComponent);
        }

        templateMap.put("components", components);

        body.put("template", templateMap);

        Map<String, Object> response = postMessageRequest(body);
        System.out.println("Meta API Image Template Response: " + response);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postMessageRequest(Map<String, Object> body) {
        String url = "https://graph.facebook.com/v18.0/" + phoneId + "/messages";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + accessToken);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            return (Map<String, Object>) restTemplate.postForObject(url, request, Map.class);
        } catch (HttpStatusCodeException e) {
            int statusCode = e.getStatusCode().value();
            String responseBody = e.getResponseBodyAsString();

            if (statusCode == 429) {
                System.err.println("[META RATE LIMIT] 429 Too Many Requests: " + responseBody);
            } else {
                System.err.println("[META API ERROR] HTTP " + statusCode + " - " + responseBody);
            }

            throw new IllegalStateException("Meta API isteği başarısız oldu. HTTP " + statusCode, e);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractWaMessageId(Map<String, Object> response) {
        if (response == null) {
            throw new IllegalStateException("Meta API response is null");
        }
        Object messagesObj = response.get("messages");
        if (!(messagesObj instanceof List<?> messages) || messages.isEmpty()) {
            throw new IllegalStateException("Meta API response missing messages[0].id");
        }
        Object first = messages.get(0);
        if (!(first instanceof Map<?, ?> firstMap)) {
            throw new IllegalStateException("Meta API response messages[0] is not an object");
        }
        Object idObj = ((Map<String, Object>) firstMap).get("id");
        if (!(idObj instanceof String id) || id.isBlank()) {
            throw new IllegalStateException("Meta API response missing messages[0].id");
        }
        return id;
    }
}
