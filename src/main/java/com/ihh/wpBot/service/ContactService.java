package com.ihh.wpBot.service;

import com.ihh.wpBot.model.Contact;
import com.ihh.wpBot.repository.ContactRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.opencsv.CSVReader;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ContactService {
        @Transactional
        public void deleteAllByUser(String username, String role) {
            if (role.equals("ADMIN")) {
                contactRepository.deleteAll();
            } else {
                contactRepository.deleteByCreatedBy(username);
            }
        }
    @Autowired
    private ContactRepository contactRepository;

    public static class ImportResult {
        public int imported;
        public int skipped;
        public ImportResult(int imported, int skipped) {
            this.imported = imported;
            this.skipped = skipped;
        }
    }

    @Transactional
    public ImportResult importFromCsv(String csvContent, String createdBy) {
        int imported = 0;
        int skipped = 0;
        System.out.println("CSV İÇERİĞİ: " + csvContent);
        Set<String> existingPhones = contactRepository.findAll().stream()
                .map(Contact::getPhone)
                .collect(Collectors.toSet());
        try (CSVReader reader = new CSVReader(new StringReader(csvContent))) {
            String[] header = reader.readNext();
            if (header == null) return new ImportResult(0, 0);
            int firstNameIdx = -1, lastNameIdx = -1, phoneIdx = -1;
            for (int i = 0; i < header.length; i++) {
                String col = header[i].trim().replaceAll("\uFEFF", "");
                if (col.equalsIgnoreCase("First Name")) firstNameIdx = i;
                if (col.equalsIgnoreCase("Last Name")) lastNameIdx = i;
                if (col.equalsIgnoreCase("Phone 1 - Value")) phoneIdx = i;
                if (phoneIdx == -1 && (col.equalsIgnoreCase("Phone") || col.equalsIgnoreCase("Telefon"))) phoneIdx = i;
            }
            if (phoneIdx == -1) return new ImportResult(0, 0);
            String[] row;
            while ((row = reader.readNext()) != null) {
                if (row.length <= phoneIdx) { skipped++; continue; }
                String firstName = (firstNameIdx != -1 && row.length > firstNameIdx) ? row[firstNameIdx].trim() : "";
                String lastName = (lastNameIdx != -1 && row.length > lastNameIdx) ? row[lastNameIdx].trim() : "";
                String name = (firstName + " " + lastName).trim();
                if (name.isEmpty()) name = "İsimsiz";
                String phoneRaw = row[phoneIdx].trim();
                String phone = cleanPhone(phoneRaw);
                if (phone.isEmpty() || phone.length() < 10 || existingPhones.contains(phone)) {
                    skipped++;
                    continue;
                }
                Contact contact = new Contact(name, phone, createdBy, LocalDateTime.now());
                contactRepository.save(contact);
                existingPhones.add(phone);
                imported++;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new ImportResult(imported, skipped);
        }
        return new ImportResult(imported, skipped);
    }

    private String cleanPhone(String phone) {
        if (phone == null) return "";
        String cleaned = phone.replaceAll("[\\s\\-\\(\\)]", "");
        if (cleaned.startsWith("+90")) {
            // already correct
            return cleaned;
        } else if (cleaned.startsWith("0")) {
            cleaned = "90" + cleaned.substring(1);
        }
        if (!cleaned.startsWith("90")) {
            cleaned = "90" + cleaned;
        }
        return cleaned;
    }

    public List<Contact> getAllContacts(String username, String role) {
        if ("ADMIN".equalsIgnoreCase(role)) {
            return contactRepository.findAll();
        } else {
            return contactRepository.findByCreatedBy(username);
        }
    }

    public List<Contact> searchContacts(String query, String username, String role) {
        List<Contact> contacts;
        if ("ADMIN".equalsIgnoreCase(role)) {
            contacts = contactRepository.findByNameContainingIgnoreCaseOrPhoneContaining(query, query);
        } else {
            contacts = contactRepository.findByCreatedBy(username).stream()
                    .filter(c -> c.getName().toLowerCase().contains(query.toLowerCase()) || c.getPhone().contains(query))
                    .collect(Collectors.toList());
        }
        return contacts;
    }

    public boolean deleteContact(Long id, String username, String role) {
        Optional<Contact> contactOpt = contactRepository.findById(id);
        if (!contactOpt.isPresent()) return false;
        Contact contact = contactOpt.get();
        if ("ADMIN".equalsIgnoreCase(role) || username.equals(contact.getCreatedBy())) {
            contactRepository.deleteById(id);
            return true;
        }
        return false;
    }
}
