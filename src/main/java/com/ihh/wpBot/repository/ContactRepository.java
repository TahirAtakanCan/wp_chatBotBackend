package com.ihh.wpBot.repository;

import com.ihh.wpBot.model.Contact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContactRepository extends JpaRepository<Contact, Long> {
    List<Contact> findByCreatedBy(String createdBy);
    List<Contact> findByNameContainingIgnoreCaseOrPhoneContaining(String name, String phone);
}
