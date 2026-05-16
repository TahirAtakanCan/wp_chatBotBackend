package com.ihh.wpBot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ihh.wpBot.model.Conversation;
import com.ihh.wpBot.model.ConversationStatus;
import com.ihh.wpBot.model.DeliveryRecord;
import com.ihh.wpBot.model.DeliveryStatus;
import com.ihh.wpBot.model.Message;
import com.ihh.wpBot.repository.ConversationRepository;
import com.ihh.wpBot.repository.DeliveryRecordRepository;
import com.ihh.wpBot.repository.MessageRepository;
import com.ihh.wpBot.repository.WebhookEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookEventServiceTest {

    private static final PlatformTransactionManager NOOP_TX_MANAGER = new PlatformTransactionManager() {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    };

    private static final ZoneId APP_ZONE = ZoneId.of("Europe/Istanbul");

    @Test
    void saveIncomingPayload_parsesEpochSecondsTimestamp() {
        WebhookEventRepository webhookEventRepository = mock(WebhookEventRepository.class);
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        DeliveryRecordRepository deliveryRecordRepository = mock(DeliveryRecordRepository.class);

        when(webhookEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(conversationRepository.findByPhoneNumber(any())).thenReturn(Optional.empty());
        when(conversationRepository.save(any())).thenAnswer(inv -> {
            Conversation c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(1L);
            }
            if (c.getStatus() == null) {
                c.setStatus(ConversationStatus.OPEN);
            }
            return c;
        });
        when(messageRepository.save(any())).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            if (m.getId() == null) {
                m.setId(100L);
            }
            return m;
        });

        WebhookEventService service = new WebhookEventService(
                webhookEventRepository,
                new ObjectMapper(),
                conversationRepository,
                messageRepository,
                deliveryRecordRepository,
                NOOP_TX_MANAGER,
                APP_ZONE,
                createMediaServiceMock()
        );

        String payload = """
                {
                  "entry": [{
                    "changes": [{
                      "value": {
                        "contacts": [
                          { "profile": { "name": "Atakan Can" }, "wa_id": "905071610354" }
                        ],
                        "messages": [{
                          "from": "905071610354",
                          "id": "wamid.test_epoch",
                          "timestamp": "1714999400",
                          "type": "text",
                          "text": { "body": "hello" }
                        }]
                      }
                    }]
                  }]
                }
                """;

        service.saveIncomingPayload(payload);

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository, times(1)).save(messageCaptor.capture());
        Message saved = messageCaptor.getValue();

        LocalDateTime expected = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(1714999400L),
                APP_ZONE
        );

        assertNotNull(saved.getSentAt());
        assertEquals(expected, saved.getSentAt());
        assertTrue(saved.getSentAt().getYear() >= 2020, "sentAt should not be suspiciously old");
    }

    @Test
    void saveIncomingPayload_missingTimestamp_fallsBackToNow() {
        WebhookEventRepository webhookEventRepository = mock(WebhookEventRepository.class);
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        DeliveryRecordRepository deliveryRecordRepository = mock(DeliveryRecordRepository.class);

        when(webhookEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(conversationRepository.findByPhoneNumber(any())).thenReturn(Optional.empty());
        when(conversationRepository.save(any())).thenAnswer(inv -> {
            Conversation c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(1L);
            }
            return c;
        });
        when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WebhookEventService service = new WebhookEventService(
                webhookEventRepository,
                new ObjectMapper(),
                conversationRepository,
                messageRepository,
                deliveryRecordRepository,
                NOOP_TX_MANAGER,
                APP_ZONE,
                createMediaServiceMock()
        );

        LocalDateTime before = LocalDateTime.now(APP_ZONE);
        String payloadMissingTs = """
                {
                  "entry": [{
                    "changes": [{
                      "value": {
                        "contacts": [
                          { "profile": { "name": "Atakan Can" }, "wa_id": "905071610354" }
                        ],
                        "messages": [{
                          "from": "905071610354",
                          "id": "wamid.test_missing_ts",
                          "type": "text",
                          "text": { "body": "hello" }
                        }]
                      }
                    }]
                  }]
                }
                """;

        service.saveIncomingPayload(payloadMissingTs);
        LocalDateTime after = LocalDateTime.now(APP_ZONE);

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository, times(1)).save(messageCaptor.capture());
        Message saved = messageCaptor.getValue();

        assertNotNull(saved.getSentAt());
        assertTrue(!saved.getSentAt().isBefore(before.minusSeconds(5)) && !saved.getSentAt().isAfter(after.plusSeconds(5)),
                "sentAt should be close to now when timestamp is missing");
    }

    @Test
    void saveIncomingPayload_invalidTimestamp_fallsBackToNow_withoutThrowing() {
        WebhookEventRepository webhookEventRepository = mock(WebhookEventRepository.class);
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        DeliveryRecordRepository deliveryRecordRepository = mock(DeliveryRecordRepository.class);

        when(webhookEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(conversationRepository.findByPhoneNumber(any())).thenReturn(Optional.empty());
        when(conversationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WebhookEventService service = new WebhookEventService(
                webhookEventRepository,
                new ObjectMapper(),
                conversationRepository,
                messageRepository,
                deliveryRecordRepository,
                NOOP_TX_MANAGER,
                APP_ZONE,
                createMediaServiceMock()
        );

        LocalDateTime before = LocalDateTime.now(APP_ZONE);
        String payloadBadTs = """
                {
                  "entry": [{
                    "changes": [{
                      "value": {
                        "contacts": [
                          { "profile": { "name": "Atakan Can" }, "wa_id": "905071610354" }
                        ],
                        "messages": [{
                          "from": "905071610354",
                          "id": "wamid.test_bad_ts",
                          "timestamp": "abc",
                          "type": "text",
                          "text": { "body": "hello" }
                        }]
                      }
                    }]
                  }]
                }
                """;

        service.saveIncomingPayload(payloadBadTs);
        LocalDateTime after = LocalDateTime.now(APP_ZONE);

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository, times(1)).save(messageCaptor.capture());
        Message saved = messageCaptor.getValue();

        assertNotNull(saved.getSentAt());
        long diffSeconds = Math.abs(ChronoUnit.SECONDS.between(saved.getSentAt(), before));
        assertTrue(diffSeconds <= 5 || (!saved.getSentAt().isBefore(before.minusSeconds(5)) && !saved.getSentAt().isAfter(after.plusSeconds(5))),
                "sentAt should fall back close to now for invalid timestamp");
    }

    @Test
    void saveIncomingPayload_setsContactNameForNewConversation_fromMetaContactsProfileName() {
        WebhookEventRepository webhookEventRepository = mock(WebhookEventRepository.class);
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        DeliveryRecordRepository deliveryRecordRepository = mock(DeliveryRecordRepository.class);

        when(webhookEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(conversationRepository.findByPhoneNumber(any())).thenReturn(Optional.empty());
        when(conversationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WebhookEventService service = new WebhookEventService(
                webhookEventRepository,
                new ObjectMapper(),
                conversationRepository,
                messageRepository,
                deliveryRecordRepository,
                NOOP_TX_MANAGER,
                APP_ZONE,
                createMediaServiceMock()
        );

        String payload = """
                {
                  "entry": [{
                    "changes": [{
                      "value": {
                        "contacts": [
                          { "profile": { "name": "Atakan Can" }, "wa_id": "905071610354" }
                        ],
                        "messages": [{
                          "from": "905071610354",
                          "id": "wamid.test_contact_name_new",
                          "type": "text",
                          "text": { "body": "hello" }
                        }]
                      }
                    }]
                  }]
                }
                """;

        service.saveIncomingPayload(payload);

        ArgumentCaptor<Conversation> convCaptor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository, times(1)).save(convCaptor.capture());
        Conversation saved = convCaptor.getValue();
        assertEquals("Atakan Can", saved.getContactName());
    }

    @Test
    void saveIncomingPayload_fillsContactNameIfExistingButBlank_doesNotOverrideIfAlreadySet() {
        WebhookEventRepository webhookEventRepository = mock(WebhookEventRepository.class);
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        DeliveryRecordRepository deliveryRecordRepository = mock(DeliveryRecordRepository.class);

        when(webhookEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Conversation existingBlank = new Conversation();
        existingBlank.setId(10L);
        existingBlank.setPhoneNumber("+905071610354");
        existingBlank.setContactName("   ");
        existingBlank.setStatus(ConversationStatus.OPEN);

        when(conversationRepository.findByPhoneNumber(any()))
                .thenReturn(Optional.of(existingBlank));
        when(conversationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WebhookEventService service = new WebhookEventService(
                webhookEventRepository,
                new ObjectMapper(),
                conversationRepository,
                messageRepository,
                deliveryRecordRepository,
                NOOP_TX_MANAGER,
                APP_ZONE,
                createMediaServiceMock()
        );

        String payload = """
                {
                  "entry": [{
                    "changes": [{
                      "value": {
                        "contacts": [
                          { "profile": { "name": "Atakan Can" }, "wa_id": "905071610354" }
                        ],
                        "messages": [{
                          "from": "905071610354",
                          "id": "wamid.test_contact_name_existing_blank",
                          "type": "text",
                          "text": { "body": "hello" }
                        }]
                      }
                    }]
                  }]
                }
                """;

        service.saveIncomingPayload(payload);

        ArgumentCaptor<Conversation> convCaptor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository, times(1)).save(convCaptor.capture());
        Conversation saved = convCaptor.getValue();
        assertEquals("Atakan Can", saved.getContactName());

        // Now simulate an existing conversation where contactName is already set; it should not be overridden.
        Conversation existingSet = new Conversation();
        existingSet.setId(11L);
        existingSet.setPhoneNumber("+905071610354");
        existingSet.setContactName("Manual Name");
        existingSet.setStatus(ConversationStatus.OPEN);

        when(conversationRepository.findByPhoneNumber(any()))
                .thenReturn(Optional.of(existingSet));

        service.saveIncomingPayload(payload);

        ArgumentCaptor<Conversation> convCaptor2 = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository, times(2)).save(convCaptor2.capture());
        Conversation saved2 = convCaptor2.getAllValues().get(1);
        assertEquals("Manual Name", saved2.getContactName());
    }

    @Test
    void saveIncomingPayload_defensiveMissingContacts_doesNotSetContactName() {
        WebhookEventRepository webhookEventRepository = mock(WebhookEventRepository.class);
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        DeliveryRecordRepository deliveryRecordRepository = mock(DeliveryRecordRepository.class);

        when(webhookEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(conversationRepository.findByPhoneNumber(any())).thenReturn(Optional.empty());
        when(conversationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WebhookEventService service = new WebhookEventService(
                webhookEventRepository,
                new ObjectMapper(),
                conversationRepository,
                messageRepository,
                deliveryRecordRepository,
                NOOP_TX_MANAGER,
                APP_ZONE,
                createMediaServiceMock()
        );

        String payloadNoContacts = """
                {
                  "entry": [{
                    "changes": [{
                      "value": {
                        "messages": [{
                          "from": "905071610354",
                          "id": "wamid.test_contact_name_missing_contacts",
                          "type": "text",
                          "text": { "body": "hello" }
                        }]
                      }
                    }]
                  }]
                }
                """;

        service.saveIncomingPayload(payloadNoContacts);

        ArgumentCaptor<Conversation> convCaptor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository, times(1)).save(convCaptor.capture());
        Conversation saved = convCaptor.getValue();
        assertNull(saved.getContactName());
    }

    @Test
    void saveIncomingPayload_statusEvent_updatesMessageAndDeliveryRecord() {
        WebhookEventRepository webhookEventRepository = mock(WebhookEventRepository.class);
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        DeliveryRecordRepository deliveryRecordRepository = mock(DeliveryRecordRepository.class);

        when(webhookEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DeliveryRecord deliveryRecord = new DeliveryRecord();
        deliveryRecord.setWaMessageId("wamid.status.test");
        deliveryRecord.setStatus(DeliveryStatus.SENT);
        when(deliveryRecordRepository.findByWaMessageId("wamid.status.test")).thenReturn(Optional.of(deliveryRecord));
        when(deliveryRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WebhookEventService service = new WebhookEventService(
                webhookEventRepository,
                new ObjectMapper(),
                conversationRepository,
                messageRepository,
                deliveryRecordRepository,
                NOOP_TX_MANAGER,
                APP_ZONE,
                createMediaServiceMock()
        );

        String payload = """
                {
                  "entry": [{
                    "changes": [{
                      "value": {
                        "statuses": [{
                          "id": "wamid.status.test",
                          "status": "failed",
                          "recipient_id": "905071610354",
                          "errors": [
                            { "code": 131049, "title": "Message failed due to quality issues" }
                          ]
                        }]
                      }
                    }]
                  }]
                }
                """;

        service.saveIncomingPayload(payload);

        assertEquals(DeliveryStatus.FAILED, deliveryRecord.getStatus());
        assertEquals("131049", deliveryRecord.getFailureCode());
        assertEquals("Message failed due to quality issues", deliveryRecord.getFailureReason());
        assertNotNull(deliveryRecord.getFailedAt());
    }

    @Test
    void saveIncomingPayload_imageMessage_storesCaptionAndMediaFields_withoutMediaPlaceholder() {
        WebhookEventRepository webhookEventRepository = mock(WebhookEventRepository.class);
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        DeliveryRecordRepository deliveryRecordRepository = mock(DeliveryRecordRepository.class);
        WhatsAppMediaService mediaService = mock(WhatsAppMediaService.class);

        when(webhookEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(conversationRepository.findByPhoneNumber(any())).thenReturn(Optional.empty());
        when(conversationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mediaService.downloadIncomingMedia("media-123")).thenReturn(
                Optional.of(new WhatsAppMediaService.StoredMedia("uploads/inbound/media-123.jpg", "image/jpeg"))
        );

        WebhookEventService service = new WebhookEventService(
                webhookEventRepository,
                new ObjectMapper(),
                conversationRepository,
                messageRepository,
                deliveryRecordRepository,
                NOOP_TX_MANAGER,
                APP_ZONE,
                mediaService
        );

        String payload = """
                {
                  "entry": [{
                    "changes": [{
                      "value": {
                        "messages": [{
                          "from": "905071610354",
                          "id": "wamid.image.test",
                          "timestamp": "1714999400",
                          "type": "image",
                          "image": {
                            "id": "media-123",
                            "mime_type": "image/jpeg",
                            "caption": "Merhaba 😊"
                          }
                        }]
                      }
                    }]
                  }]
                }
                """;

        service.saveIncomingPayload(payload);

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository, times(1)).save(messageCaptor.capture());
        Message saved = messageCaptor.getValue();

        assertEquals("Merhaba 😊", saved.getContent());
        assertEquals("Merhaba 😊", saved.getCaption());
        assertEquals("media-123", saved.getMediaId());
        assertEquals("/api/media/media-123", saved.getMediaUrl());
        assertEquals("image/jpeg", saved.getMimeType());
        assertEquals("uploads/inbound/media-123.jpg", saved.getMediaStoragePath());
        assertTrue(!"[medya]".equals(saved.getContent()));
    }

    private WhatsAppMediaService createMediaServiceMock() {
        return mock(WhatsAppMediaService.class);
    }
}

