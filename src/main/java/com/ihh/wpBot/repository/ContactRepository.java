package com.ihh.wpBot.repository;

import com.ihh.wpBot.model.Contact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContactRepository extends JpaRepository<Contact, Long> {
    List<Contact> findByCreatedBy(String createdBy);
    List<Contact> findByNameContainingIgnoreCaseOrPhoneContaining(String name, String phone);
    void deleteByCreatedBy(String createdBy);

    Optional<Contact> findByPhone(String phone);
}
