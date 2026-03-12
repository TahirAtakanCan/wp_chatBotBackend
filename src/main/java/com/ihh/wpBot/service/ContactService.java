package com.ihh.wpBot.service;

import com.ihh.wpBot.model.Contact;
import com.ihh.wpBot.repository.ContactRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ContactService {

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

    // ── Excel import ──────────────────────────────────────────────────────────
    @Transactional
    public ImportResult importFromExcel(byte[] excelBytes, String createdBy) {
        int imported = 0, skipped = 0;
        Set<String> existingPhones = contactRepository.findAll().stream()
                .map(Contact::getPhone).collect(Collectors.toSet());
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) return new ImportResult(0, 0);

            int firstNameIdx = -1, lastNameIdx = -1, phoneIdx = -1;
            for (Cell cell : headerRow) {
                String col = cell.getStringCellValue().trim()
                        .replaceAll("\uFEFF", "").toLowerCase();
                int idx = cell.getColumnIndex();
                if (col.equals("first name") || col.equals("ad"))     firstNameIdx = idx;
                if (col.equals("last name")  || col.equals("soyad"))  lastNameIdx  = idx;
                if (col.equals("phone 1 - value") || col.equals("phone")
                        || col.equals("telefon"))                      phoneIdx     = idx;
            }
            if (phoneIdx == -1) return new ImportResult(0, 0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) { skipped++; continue; }
                String firstName = firstNameIdx != -1
                        ? getCellString(row.getCell(firstNameIdx)) : "";
                String lastName  = lastNameIdx  != -1
                        ? getCellString(row.getCell(lastNameIdx))  : "";
                String name  = (firstName + " " + lastName).trim();
                if (name.isEmpty()) name = "İsimsiz";
                String phone = cleanPhone(getCellString(row.getCell(phoneIdx)));
                if (phone.isEmpty() || phone.length() < 10
                        || existingPhones.contains(phone)) { skipped++; continue; }
                contactRepository.save(
                        new Contact(name, phone, createdBy, LocalDateTime.now()));
                existingPhones.add(phone);
                imported++;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new ImportResult(imported, skipped);
        }
        return new ImportResult(imported, skipped);
    }

    @Transactional
public ImportResult importFromGoogleSheets(String csvContent, String createdBy) {
    int imported = 0, skipped = 0;
    Set<String> existingPhones = contactRepository.findAll().stream()
            .map(Contact::getPhone).collect(Collectors.toSet());
    try (BufferedReader reader = new BufferedReader(new StringReader(csvContent))) {

        String headerLine = reader.readLine();
        if (headerLine == null) return new ImportResult(0, 0);

        String line;
        int lineNumber = 1;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (line.isBlank()) {
                System.out.println("[ATLANDI] Satır " + lineNumber + ": Boş satır");
                skipped++; continue;
            }
            String[] row = parseCsvLine(line);
            if (row.length < 2) {
                System.out.println("[ATLANDI] Satır " + lineNumber + ": Kolon sayısı yetersiz → " + line);
                skipped++; continue;
            }

            String phoneRaw = row[0].trim();
            String name     = row[1].trim();
            if (name.isEmpty()) name = "İsimsiz";
            String phone = cleanPhone(phoneRaw);

            if (phone.isEmpty()) {
                System.out.println("[ATLANDI] Satır " + lineNumber + ": Telefon boş → İsim: " + name);
                skipped++; continue;
            }
            if (phone.length() < 10) {
                System.out.println("[ATLANDI] Satır " + lineNumber + ": Telefon çok kısa → " + phone + " | İsim: " + name);
                skipped++; continue;
            }
            if (existingPhones.contains(phone)) {
                System.out.println("[ATLANDI] Satır " + lineNumber + ": Zaten kayıtlı → " + phone + " | İsim: " + name);
                skipped++; continue;
            }

            contactRepository.save(
                    new Contact(name, phone, createdBy, LocalDateTime.now()));
            existingPhones.add(phone);
            imported++;
        }
    } catch (Exception e) {
        e.printStackTrace();
        return new ImportResult(imported, skipped);
    }
    return new ImportResult(imported, skipped);
}

    // Basit CSV satırı parse (tırnak içi virgülleri korur)
    private String[] parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        result.add(sb.toString());
        return result.toArray(new String[0]);
    }

    // ── Yardımcı metodlar ─────────────────────────────────────────────────────
    private String getCellString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default      -> "";
        };
    }

    private String cleanPhone(String phone) {
        if (phone == null) return "";
        String cleaned = phone.replaceAll("[\\s\\-\\(\\)]", "");
        if (cleaned.startsWith("+90")) return cleaned;
        if (cleaned.startsWith("0"))   cleaned = "90" + cleaned.substring(1);
        if (!cleaned.startsWith("90")) cleaned = "90" + cleaned;
        return cleaned;
    }

    @Transactional
    public void deleteAllByUser(String username, String role) {
        if (role.equals("ADMIN")) contactRepository.deleteAll();
        else contactRepository.deleteByCreatedBy(username);
    }

    public List<Contact> getAllContacts(String username, String role) {
        if ("ADMIN".equalsIgnoreCase(role)) return contactRepository.findAll();
        return contactRepository.findByCreatedBy(username);
    }

    public List<Contact> searchContacts(String query, String username, String role) {
        if ("ADMIN".equalsIgnoreCase(role)) {
            return contactRepository
                    .findByNameContainingIgnoreCaseOrPhoneContaining(query, query);
        }
        return contactRepository.findByCreatedBy(username).stream()
                .filter(c -> c.getName().toLowerCase().contains(query.toLowerCase())
                        || c.getPhone().contains(query))
                .collect(Collectors.toList());
    }

    public boolean deleteContact(Long id, String username, String role) {
        Optional<Contact> contactOpt = contactRepository.findById(id);
        if (contactOpt.isEmpty()) return false;
        Contact contact = contactOpt.get();
        if ("ADMIN".equalsIgnoreCase(role)
                || username.equals(contact.getCreatedBy())) {
            contactRepository.deleteById(id);
            return true;
        }
        return false;
    }
}