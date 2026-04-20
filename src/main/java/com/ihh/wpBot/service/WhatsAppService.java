package com.ihh.wpBot.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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

    @Value("${meta.phone.id}")
    private String phoneId;

    @Value("${meta.access.token}")
    private String accessToken;

    private final RestTemplate restTemplate = new RestTemplate();

    public void sendTemplateMessage(String toPhoneNumber, String templateName, String languageCode) {
        Map<String, Object> body = new HashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("to", toPhoneNumber);
        body.put("type", "template");

        Map<String, Object> templateMap = new HashMap<>();
        templateMap.put("name", templateName);

        Map<String, Object> languageMap = new HashMap<>();
        languageMap.put("code", languageCode);

        templateMap.put("language", languageMap);
        body.put("template", templateMap);

        Map<String, Object> response = postMessageRequest(body);
        System.out.println("Meta API Response: " + response);
    }

    public void sendImageTemplateMessage(String toPhoneNumber, String templateName,
                                         String languageCode, String imageUrl) {
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
        templateMap.put("components", components);

        body.put("template", templateMap);

        Map<String, Object> response = postMessageRequest(body);
        System.out.println("Meta API Image Template Response: " + response);
    }

    private Map<String, Object> postMessageRequest(Map<String, Object> body) {
        String url = "https://graph.facebook.com/v18.0/" + phoneId + "/messages";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + accessToken);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            return restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            ).getBody();
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
}
