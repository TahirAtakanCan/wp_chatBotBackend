package com.ihh.wpBot.controller;

import com.ihh.wpBot.dto.ExportColumn;
import com.ihh.wpBot.dto.ExportOptions;
import com.ihh.wpBot.dto.ExportOptionsRequest;
import com.ihh.wpBot.dto.SortBy;
import com.ihh.wpBot.model.DeliveryRecord;
import com.ihh.wpBot.model.DeliveryStatus;
import com.ihh.wpBot.repository.DeliveryRecordRepository;
import com.ihh.wpBot.service.DeliveryExportService;
import com.ihh.wpBot.service.FailureCategoryMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/delivery")
public class DeliveryController {

    private final DeliveryRecordRepository deliveryRecordRepository;
    private final DeliveryExportService exportService;

    public DeliveryController(DeliveryRecordRepository repo, DeliveryExportService exportService) {
        this.deliveryRecordRepository = repo;
        this.exportService = exportService;
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

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportDeliveries(
            @RequestParam(required = false) DeliveryStatus status,
            @RequestParam(required = false) Integer days
    ) throws IOException {
                ExportOptions options = new ExportOptions();
                options.setStatus(status);
                if (days != null && days > 0) {
                        options.setSinceDate(LocalDateTime.now().minusDays(days));
                }
                return buildExportResponse(options);
        }

        @PostMapping("/export")
        public ResponseEntity<byte[]> exportDeliveries(
                        @RequestBody ExportOptionsRequest request
        ) throws IOException {
                return buildExportResponse(toExportOptions(request));
        }

        @GetMapping("/failure-categories")
        public ResponseEntity<List<Map<String, String>>> getFailureCategories() {
                return ResponseEntity.ok(FailureCategoryMapper.getAllCategories());
        }

        private ResponseEntity<byte[]> buildExportResponse(ExportOptions options) throws IOException {
                byte[] data = exportService.exportToExcel(options);

                String filename = String.format("gonderim_raporu_%s.xlsx",
                                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")));

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                ));
                headers.setContentDispositionFormData("attachment", filename);
                headers.setContentLength(data.length);

                return new ResponseEntity<>(data, headers, HttpStatus.OK);
        }

        private ExportOptions toExportOptions(ExportOptionsRequest request) {
                ExportOptions options = new ExportOptions();
                if (request == null) {
                        return options;
                }

                options.setStatus(parseDeliveryStatus(request.getStatus()));
                if (request.getDays() != null && request.getDays() > 0) {
                        options.setSinceDate(LocalDateTime.now().minusDays(request.getDays()));
                }
                options.setFailureCodes(request.getFailureCodes());
                options.setTemplateName(request.getTemplateName());
                options.setPhoneSearch(request.getPhoneSearch());
                options.setContactNameSearch(request.getContactNameSearch());
                options.setColumns(parseColumns(request.getColumns()));
                options.setSortBy(parseSortBy(request.getSortBy()));
                return options;
        }

        private DeliveryStatus parseDeliveryStatus(String value) {
                if (value == null || value.isBlank()) {
                        return null;
                }
                try {
                        return DeliveryStatus.valueOf(value.trim().toUpperCase());
                } catch (IllegalArgumentException ex) {
                        return null;
                }
        }

        private Set<ExportColumn> parseColumns(List<String> columns) {
                if (columns == null || columns.isEmpty()) {
                        return null;
                }

                Set<ExportColumn> parsed = columns.stream()
                                .map(this::parseExportColumn)
                                .filter(column -> column != null)
                                .collect(Collectors.toSet());

                return parsed.isEmpty() ? null : parsed;
        }

        private ExportColumn parseExportColumn(String value) {
                if (value == null || value.isBlank()) {
                        return null;
                }
                try {
                        return ExportColumn.valueOf(value.trim().toUpperCase());
                } catch (IllegalArgumentException ex) {
                        return null;
                }
        }

        private SortBy parseSortBy(String value) {
                if (value == null || value.isBlank()) {
                        return SortBy.SENT_AT_DESC;
                }
                try {
                        return SortBy.valueOf(value.trim().toUpperCase());
                } catch (IllegalArgumentException ex) {
                        return SortBy.SENT_AT_DESC;
                }
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
