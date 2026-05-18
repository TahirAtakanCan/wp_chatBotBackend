package com.ihh.wpBot.service;

import com.ihh.wpBot.controller.dto.MetaTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class MetaTemplateService {

    private static final Logger log = LoggerFactory.getLogger(MetaTemplateService.class);

    @Value("${meta.waba.id}")
    private String wabaId;

    @Value("${meta.access.token}")
    private String accessToken;

    @Value("${meta.graph.api.version:v23.0}")
    private String graphApiVersion;

    private final RestTemplate restTemplate;

    public MetaTemplateService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Cacheable(value = "metaTemplates", unless = "#result == null || #result.isEmpty()")
    public List<MetaTemplate> fetchAllTemplates() {
        log.info("Fetching templates from Meta API, wabaId={}", wabaId);

        String url = String.format(
                "https://graph.facebook.com/%s/%s/message_templates?limit=100",
                graphApiVersion, wabaId
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            if (response.getBody() == null || !response.getBody().containsKey("data")) {
                log.warn("Meta API empty response for message templates");
                return Collections.emptyList();
            }

            List<Map<String, Object>> rawTemplates = (List<Map<String, Object>>) response.getBody().get("data");
            List<MetaTemplate> result = new ArrayList<>();
            for (Map<String, Object> raw : rawTemplates) {
                try {
                    result.add(parseMetaTemplate(raw));
                } catch (Exception e) {
                    log.warn("Error parsing template {}, skipping", raw.get("name"));
                }
            }
            log.info("Fetched {} templates from Meta", result.size());
            return result;
        } catch (Exception e) {
            log.error("Error fetching Meta templates", e);
            throw new RuntimeException("Meta API hatası: " + e.getMessage(), e);
        }
    }

    @CacheEvict(value = "metaTemplates", allEntries = true)
    public void refreshCache() {
        log.info("Meta templates cache cleared");
    }

    public Optional<MetaTemplate> findByName(String name) {
        return fetchAllTemplates().stream()
                .filter(t -> t.getName().equals(name))
                .findFirst();
    }

    private MetaTemplate parseMetaTemplate(Map<String, Object> raw) {
        MetaTemplate template = new MetaTemplate();
        template.setName((String) raw.get("name"));
        template.setLanguage((String) raw.get("language"));
        template.setStatus((String) raw.get("status"));
        template.setCategory((String) raw.get("category"));

        List<Map<String, Object>> components = (List<Map<String, Object>>) raw.get("components");
        if (components != null) {
            for (Map<String, Object> component : components) {
                String type = (String) component.get("type");
                if ("HEADER".equalsIgnoreCase(type)) {
                    String format = (String) component.get("format");
                    template.setHeaderType(format != null ? format.toUpperCase() : "NONE");
                } else if ("BODY".equalsIgnoreCase(type)) {
                    template.setBodyText((String) component.get("text"));
                }
            }
        }
        if (template.getHeaderType() == null) {
            template.setHeaderType("NONE");
        }
        return template;
    }
}
