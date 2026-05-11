package com.ihh.wpBot.repository;

import com.ihh.wpBot.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    Optional<Message> findByWaMessageId(String waMessageId);

    Page<Message> findByConversationIdOrderBySentAtAsc(Long conversationId, Pageable pageable);

    Optional<Message> findByIdAndConversationId(Long id, Long conversationId);

    @Modifying
    long deleteByConversationId(Long conversationId);
}

