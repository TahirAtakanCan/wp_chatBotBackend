package com.ihh.wpBot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ihh.wpBot.model.Conversation;
import com.ihh.wpBot.model.ConversationStatus;
import com.ihh.wpBot.model.Message;
import com.ihh.wpBot.repository.ConversationRepository;
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

    @Test
    void saveIncomingPayload_parsesEpochSecondsTimestamp() {
        WebhookEventRepository webhookEventRepository = mock(WebhookEventRepository.class);
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);

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
                NOOP_TX_MANAGER
        );

        String payload = """
                {
                  "entry": [{
                    "changes": [{
                      "value": {
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
                ZoneId.systemDefault()
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
                NOOP_TX_MANAGER
        );

        LocalDateTime before = LocalDateTime.now();
        String payloadMissingTs = """
                {
                  "entry": [{
                    "changes": [{
                      "value": {
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
        LocalDateTime after = LocalDateTime.now();

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

        when(webhookEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(conversationRepository.findByPhoneNumber(any())).thenReturn(Optional.empty());
        when(conversationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WebhookEventService service = new WebhookEventService(
                webhookEventRepository,
                new ObjectMapper(),
                conversationRepository,
                messageRepository,
                NOOP_TX_MANAGER
        );

        LocalDateTime before = LocalDateTime.now();
        String payloadBadTs = """
                {
                  "entry": [{
                    "changes": [{
                      "value": {
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
        LocalDateTime after = LocalDateTime.now();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository, times(1)).save(messageCaptor.capture());
        Message saved = messageCaptor.getValue();

        assertNotNull(saved.getSentAt());
        long diffSeconds = Math.abs(ChronoUnit.SECONDS.between(saved.getSentAt(), before));
        assertTrue(diffSeconds <= 5 || (!saved.getSentAt().isBefore(before.minusSeconds(5)) && !saved.getSentAt().isAfter(after.plusSeconds(5))),
                "sentAt should fall back close to now for invalid timestamp");
    }
}

