package com.ihh.wpBot.service;

import com.ihh.wpBot.dto.ExportColumn;
import com.ihh.wpBot.dto.ExportOptions;
import com.ihh.wpBot.dto.SortBy;
import com.ihh.wpBot.model.DeliveryRecord;
import com.ihh.wpBot.model.DeliveryStatus;
import com.ihh.wpBot.repository.DeliveryRecordRepository;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DeliveryExportService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryExportService.class);
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DeliveryRecordRepository deliveryRecordRepository;

    public DeliveryExportService(DeliveryRecordRepository repo) {
        this.deliveryRecordRepository = repo;
    }

    public byte[] exportToExcel(DeliveryStatus status, LocalDateTime sinceDate) throws IOException {
        ExportOptions options = new ExportOptions();
        options.setStatus(status);
        options.setSinceDate(sinceDate);
        return exportToExcel(options);
    }

    public byte[] exportToExcel(ExportOptions options) throws IOException {
        ExportOptions safeOptions = options != null ? options : new ExportOptions();
        List<DeliveryRecord> records = fetchFilteredRecords(safeOptions);
        Set<ExportColumn> selectedColumns = resolveColumns(safeOptions.getColumns());
        List<ExportColumn> orderedColumns = java.util.Arrays.stream(ExportColumn.values())
                .filter(selectedColumns::contains)
                .collect(Collectors.toList());

        log.info("Excel export: {} kayıt, status={}, sinceDate={}, failureCodes={}, templateName={}, phoneSearch={}, contactNameSearch={}, columns={}, sortBy={}",
                records.size(), safeOptions.getStatus(), safeOptions.getSinceDate(), safeOptions.getFailureCodes(),
                safeOptions.getTemplateName(), safeOptions.getPhoneSearch(), safeOptions.getContactNameSearch(),
                orderedColumns, safeOptions.getSortBy());

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Gönderim Raporu");

            CellStyle headerStyle = buildHeaderStyle(workbook);
            CellStyle cellStyle = buildBaseCellStyle(workbook);
            CellStyle failedStyle = buildColoredStyle(workbook, cellStyle, IndexedColors.ROSE);
            CellStyle successStyle = buildColoredStyle(workbook, cellStyle, IndexedColors.LIGHT_GREEN);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < orderedColumns.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(getColumnLabel(orderedColumns.get(i)));
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            for (DeliveryRecord record : records) {
                Row row = sheet.createRow(rowNum);

                CellStyle rowStyle;
                if (record.getStatus() == DeliveryStatus.FAILED) {
                    rowStyle = failedStyle;
                } else if (record.getStatus() == DeliveryStatus.DELIVERED
                        || record.getStatus() == DeliveryStatus.READ) {
                    rowStyle = successStyle;
                } else {
                    rowStyle = cellStyle;
                }

                for (int colIdx = 0; colIdx < orderedColumns.size(); colIdx++) {
                    createCell(row, colIdx, getCellValue(orderedColumns.get(colIdx), record, rowNum), rowStyle);
                }

                rowNum++;
            }

            if (!orderedColumns.isEmpty() && rowNum > 1) {
                sheet.setAutoFilter(new CellRangeAddress(0, rowNum - 1, 0, orderedColumns.size() - 1));
            }

            for (int i = 0; i < orderedColumns.size(); i++) {
                sheet.setColumnWidth(i, getColumnWidth(orderedColumns.get(i)));
            }

            sheet.createFreezePane(0, 1);

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                workbook.write(out);
                return out.toByteArray();
            }
        }
    }

    private List<DeliveryRecord> fetchFilteredRecords(ExportOptions options) {
        List<DeliveryRecord> records;

        if (options.getStatus() != null && options.getSinceDate() != null) {
            records = deliveryRecordRepository.findByStatusAndSentAtAfterOrderBySentAtDesc(options.getStatus(), options.getSinceDate());
        } else if (options.getStatus() != null) {
            records = deliveryRecordRepository.findByStatusOrderBySentAtDesc(options.getStatus());
        } else if (options.getSinceDate() != null) {
            records = deliveryRecordRepository.findBySentAtAfterOrderBySentAtDesc(options.getSinceDate());
        } else {
            records = deliveryRecordRepository.findAllByOrderBySentAtDesc();
        }

        Set<String> failureCodeFilter = normalizeStrings(options.getFailureCodes());
        String templateName = normalizeValue(options.getTemplateName());
        String phoneSearch = normalizePhone(options.getPhoneSearch());
        String contactNameSearch = normalizeValue(options.getContactNameSearch());

        Comparator<DeliveryRecord> comparator = getComparator(options.getSortBy());

        return records.stream()
                .filter(record -> matchesFailureCodes(record, failureCodeFilter))
                .filter(record -> matchesTemplateName(record, templateName))
                .filter(record -> matchesPhoneSearch(record, phoneSearch))
                .filter(record -> matchesContactNameSearch(record, contactNameSearch))
                .sorted(comparator)
                .collect(Collectors.toList());
    }

    private Set<ExportColumn> resolveColumns(Set<ExportColumn> columns) {
        if (columns == null || columns.isEmpty()) {
            Set<ExportColumn> allColumns = new LinkedHashSet<>();
            java.util.Collections.addAll(allColumns, ExportColumn.values());
            return allColumns;
        }

        Set<ExportColumn> selected = new LinkedHashSet<>();
        for (ExportColumn column : ExportColumn.values()) {
            if (columns.contains(column)) {
                selected.add(column);
            }
        }
        return selected;
    }

    private boolean matchesFailureCodes(DeliveryRecord record, Set<String> failureCodes) {
        if (failureCodes == null || failureCodes.isEmpty()) {
            return true;
        }
        return failureCodes.contains(normalizeValue(record.getFailureCode()));
    }

    private boolean matchesTemplateName(DeliveryRecord record, String templateName) {
        if (templateName == null || templateName.isBlank()) {
            return true;
        }
        return normalizeValue(record.getTemplateName()).equals(templateName);
    }

    private boolean matchesPhoneSearch(DeliveryRecord record, String phoneSearch) {
        if (phoneSearch == null || phoneSearch.isBlank()) {
            return true;
        }
        return normalizePhone(record.getPhoneNumber()).contains(phoneSearch);
    }

    private boolean matchesContactNameSearch(DeliveryRecord record, String contactNameSearch) {
        if (contactNameSearch == null || contactNameSearch.isBlank()) {
            return true;
        }
        return normalizeValue(record.getContactName()).contains(contactNameSearch);
    }

    private Set<String> normalizeStrings(List<String> values) {
        if (values == null || values.isEmpty()) {
            return java.util.Collections.emptySet();
        }

        Set<String> normalized = new HashSet<>();
        for (String value : values) {
            String normalizedValue = normalizeValue(value);
            if (!normalizedValue.isBlank()) {
                normalized.add(normalizedValue);
            }
        }
        return normalized;
    }

    private String normalizeValue(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String normalizePhone(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").trim();
    }

    private Comparator<DeliveryRecord> getComparator(SortBy sortBy) {
        SortBy safeSortBy = sortBy != null ? sortBy : SortBy.SENT_AT_DESC;
        return switch (safeSortBy) {
            case SENT_AT_DESC -> Comparator.comparing(DeliveryRecord::getSentAt, Comparator.nullsLast(Comparator.reverseOrder()));
            case SENT_AT_ASC -> Comparator.comparing(DeliveryRecord::getSentAt, Comparator.nullsLast(Comparator.naturalOrder()));
            case CONTACT_NAME_ASC -> Comparator.comparing(record -> normalizeValue(record.getContactName()), Comparator.nullsLast(Comparator.naturalOrder()));
            case STATUS_ASC -> Comparator.comparing(record -> record.getStatus() != null ? record.getStatus().name() : "", Comparator.nullsLast(Comparator.naturalOrder()));
        };
    }

    private String getColumnLabel(ExportColumn column) {
        return switch (column) {
            case SIRA -> "Sıra";
            case ISIM -> "İsim";
            case TELEFON -> "Telefon";
            case SABLON -> "Şablon";
            case DURUM -> "Durum";
            case HATA_KODU -> "Hata Kodu";
            case HATA_KATEGORI -> "Hata Kategorisi";
            case HATA_DETAY -> "Hata Detayı";
            case GONDERIM_TARIHI -> "Gönderim Tarihi";
            case ILETILDI_TARIHI -> "İletildi Tarihi";
            case OKUNDU_TARIHI -> "Okundu Tarihi";
            case BASARISIZ_TARIHI -> "Başarısız Tarihi";
        };
    }

    private int getColumnWidth(ExportColumn column) {
        return switch (column) {
            case SIRA -> 8 * 256;
            case ISIM -> 25 * 256;
            case TELEFON -> 16 * 256;
            case SABLON -> 30 * 256;
            case DURUM -> 14 * 256;
            case HATA_KODU -> 12 * 256;
            case HATA_KATEGORI -> 20 * 256;
            case HATA_DETAY -> 50 * 256;
            case GONDERIM_TARIHI, ILETILDI_TARIHI, OKUNDU_TARIHI, BASARISIZ_TARIHI -> 20 * 256;
        };
    }

    private Object getCellValue(ExportColumn column, DeliveryRecord record, int rowNum) {
        return switch (column) {
            case SIRA -> rowNum;
            case ISIM -> record.getContactName() != null ? record.getContactName() : "";
            case TELEFON -> record.getPhoneNumber() != null ? record.getPhoneNumber() : "";
            case SABLON -> record.getTemplateName() != null ? record.getTemplateName() : "";
            case DURUM -> translateStatus(record.getStatus());
            case HATA_KODU -> record.getFailureCode() != null ? record.getFailureCode() : "";
            case HATA_KATEGORI -> FailureCategoryMapper.getCategory(record.getFailureCode());
            case HATA_DETAY -> FailureCategoryMapper.getDetail(record.getFailureCode(), record.getFailureReason());
            case GONDERIM_TARIHI -> formatDate(record.getSentAt());
            case ILETILDI_TARIHI -> formatDate(record.getDeliveredAt());
            case OKUNDU_TARIHI -> formatDate(record.getReadAt());
            case BASARISIZ_TARIHI -> formatDate(record.getFailedAt());
        };
    }

    private CellStyle buildHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        applyThinBorders(style);
        return style;
    }

    private CellStyle buildBaseCellStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        applyThinBorders(style);
        return style;
    }

    private CellStyle buildColoredStyle(Workbook workbook, CellStyle base, IndexedColors color) {
        CellStyle style = workbook.createCellStyle();
        style.cloneStyleFrom(base);
        style.setFillForegroundColor(color.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private void applyThinBorders(CellStyle style) {
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private void createCell(Row row, int colIdx, Object value, CellStyle style) {
        Cell cell = row.createCell(colIdx);
        if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else {
            cell.setCellValue(value != null ? value.toString() : "");
        }
        cell.setCellStyle(style);
    }

    private String formatDate(LocalDateTime dt) {
        return dt == null ? "" : dt.format(DATE_FORMAT);
    }

    private String translateStatus(DeliveryStatus status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case SENT -> "Gönderildi";
            case DELIVERED -> "İletildi";
            case READ -> "Okundu";
            case FAILED -> "Başarısız";
        };
    }
}
