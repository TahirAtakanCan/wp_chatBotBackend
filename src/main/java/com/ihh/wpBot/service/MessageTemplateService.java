package com.ihh.wpBot.service;

import com.ihh.wpBot.model.MessageTemplate;
import com.ihh.wpBot.repository.MessageTemplateRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MessageTemplateService {

    private final MessageTemplateRepository messageTemplateRepository;

    public MessageTemplateService(MessageTemplateRepository messageTemplateRepository) {
        this.messageTemplateRepository = messageTemplateRepository;
    }

    public MessageTemplate createTemplate(String title, String content, String createdBy) {
        validateTemplateData(title, content);

        MessageTemplate template = new MessageTemplate(title, content, createdBy);
        return messageTemplateRepository.save(template);
    }

    public List<MessageTemplate> getAllTemplates() {
        return messageTemplateRepository.findAll();
    }

    public List<MessageTemplate> getTemplatesByUser(String username) {
        return messageTemplateRepository.findByCreatedBy(username);
    }

    public void deleteTemplate(Long id, String requestingUsername, String role) {
        MessageTemplate template = messageTemplateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sablon bulunamadi: " + id));

        boolean isAdmin = "ADMIN".equals(role);
        boolean isOwner = template.getCreatedBy().equals(requestingUsername);

        if (!isAdmin && !isOwner) {
            throw new SecurityException("Bu sablonu silme yetkiniz yok");
        }

        messageTemplateRepository.delete(template);
    }

    public MessageTemplate updateTemplate(Long id, String title, String content) {
        validateTemplateData(title, content);

        MessageTemplate template = messageTemplateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sablon bulunamadi: " + id));

        template.setTitle(title);
        template.setContent(content);
        return messageTemplateRepository.save(template);
    }

    public MessageTemplate getTemplateById(Long id) {
        return messageTemplateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sablon bulunamadi: " + id));
    }

    private void validateTemplateData(String title, String content) {
        if (title == null || title.isBlank() || content == null || content.isBlank()) {
            throw new RuntimeException("Title ve content zorunludur");
        }
    }
}
