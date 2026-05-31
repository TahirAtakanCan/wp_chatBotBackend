package com.ihh.wpBot.repository;

import com.ihh.wpBot.model.AutoReply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AutoReplyRepository extends JpaRepository<AutoReply, Long> {

    List<AutoReply> findAllByOrderByPriorityAscCreatedAtDesc();

    List<AutoReply> findByActiveTrueOrderByPriorityAsc();

    boolean existsByCategory(String category);
}
