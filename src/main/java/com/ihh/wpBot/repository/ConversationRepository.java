package com.ihh.wpBot.repository;

import com.ihh.wpBot.model.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByPhoneNumber(String phoneNumber);

    Page<Conversation> findAllByOrderByLastMessageAtDesc(Pageable pageable);
}

