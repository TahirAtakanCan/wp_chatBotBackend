package com.ihh.wpBot.controller;

import com.ihh.wpBot.model.DeliveryRecord;
import com.ihh.wpBot.model.DeliveryStatus;
import com.ihh.wpBot.repository.DeliveryRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/delivery")
public class DeliveryController {

    private final DeliveryRecordRepository deliveryRecordRepository;

    public DeliveryController(DeliveryRecordRepository repo) {
        this.deliveryRecordRepository = repo;
    }

    @GetMapping
    public ResponseEntity<Page<DeliveryRecord>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) DeliveryStatus status,
            @RequestParam(defaultValue = "sentAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction
    ) {
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        PageRequest pageable = PageRequest.of(page, size, Sort.by(dir, sortBy));

        Page<DeliveryRecord> result = status != null
                ? deliveryRecordRepository.findByStatusOrderBySentAtDesc(status, pageable)
                : deliveryRecordRepository.findAll(pageable);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/failed")
    public ResponseEntity<Page<DeliveryRecord>> listFailed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<DeliveryRecord> result = deliveryRecordRepository
                .findByStatusOrderBySentAtDesc(DeliveryStatus.FAILED, pageable);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/lookup")
    public ResponseEntity<Map<String, DeliveryStatus>> lookupByPhones(
            @RequestBody List<String> phones
    ) {
        List<DeliveryRecord> records = deliveryRecordRepository.findLatestByPhoneNumbers(phones);
        Map<String, DeliveryStatus> result = records.stream()
                .collect(Collectors.toMap(
                        DeliveryRecord::getPhoneNumber,
                        DeliveryRecord::getStatus,
                        (existing, replacement) -> existing
                ));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/by-phone/{phone}")
    public ResponseEntity<List<DeliveryRecord>> getByPhone(@PathVariable String phone) {
        return ResponseEntity.ok(deliveryRecordRepository.findByPhoneNumberOrderBySentAtDesc(phone));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        long total = deliveryRecordRepository.count();
        long sent = deliveryRecordRepository.countByStatus(DeliveryStatus.SENT);
        long delivered = deliveryRecordRepository.countByStatus(DeliveryStatus.DELIVERED);
        long read = deliveryRecordRepository.countByStatus(DeliveryStatus.READ);
        long failed = deliveryRecordRepository.countByStatus(DeliveryStatus.FAILED);

        return ResponseEntity.ok(Map.of(
                "total", total,
                "sent", sent,
                "delivered", delivered,
                "read", read,
                "failed", failed
        ));
    }
}
