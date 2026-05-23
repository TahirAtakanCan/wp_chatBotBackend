package com.ihh.wpBot.service;

import com.ihh.wpBot.model.DeliveryRecord;
import com.ihh.wpBot.model.DeliveryStatus;
import com.ihh.wpBot.repository.DeliveryRecordRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class DeliveryExportService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryExportService.class);
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DeliveryRecordRepository deliveryRecordRepository;

    public DeliveryExportService(DeliveryRecordRepository repo) {
        this.deliveryRecordRepository = repo;
    }

    /**
     * Gönderim raporlarını Excel olarak üretir.
     *
     * @param status    filtre (null = tümü)
     * @param sinceDate başlangıç tarihi (null = limit yok)
     * @return Excel file bytes
     */
    public byte[] exportToExcel(DeliveryStatus status, LocalDateTime sinceDate) throws IOException {
        List<DeliveryRecord> records;

        if (status != null && sinceDate != null) {
            records = deliveryRecordRepository.findByStatusAndSentAtAfterOrderBySentAtDesc(status, sinceDate);
        } else if (status != null) {
            records = deliveryRecordRepository.findByStatusOrderBySentAtDesc(status);
        } else if (sinceDate != null) {
            records = deliveryRecordRepository.findBySentAtAfterOrderBySentAtDesc(sinceDate);
        } else {
            records = deliveryRecordRepository.findAllByOrderBySentAtDesc();
        }

        log.info("Excel export: {} kayıt, status={}, sinceDate={}", records.size(), status, sinceDate);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Gönderim Raporu");

            CellStyle headerStyle = buildHeaderStyle(workbook);
            CellStyle cellStyle   = buildBaseCellStyle(workbook);
            CellStyle failedStyle = buildColoredStyle(workbook, cellStyle, IndexedColors.ROSE);
            CellStyle successStyle = buildColoredStyle(workbook, cellStyle, IndexedColors.LIGHT_GREEN);

            String[] headers = {
                "Sıra", "İsim", "Telefon", "Şablon", "Durum",
                "Hata Kodu", "Hata Sebebi", "Gönderim", "İletildi", "Okundu", "Başarısız"
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
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

                createCell(row, 0, rowNum, rowStyle);
                createCell(row, 1, record.getContactName() != null ? record.getContactName() : "", rowStyle);
                createCell(row, 2, record.getPhoneNumber(), rowStyle);
                createCell(row, 3, record.getTemplateName() != null ? record.getTemplateName() : "", rowStyle);
                createCell(row, 4, translateStatus(record.getStatus()), rowStyle);
                createCell(row, 5, record.getFailureCode() != null ? record.getFailureCode() : "", rowStyle);
                createCell(row, 6, translateFailureReason(record.getFailureCode(), record.getFailureReason()), rowStyle);
                createCell(row, 7, formatDate(record.getSentAt()), rowStyle);
                createCell(row, 8, formatDate(record.getDeliveredAt()), rowStyle);
                createCell(row, 9, formatDate(record.getReadAt()), rowStyle);
                createCell(row, 10, formatDate(record.getFailedAt()), rowStyle);

                rowNum++;
            }

            sheet.setAutoFilter(new CellRangeAddress(0, rowNum - 1, 0, headers.length - 1));

            int[] colWidths = {8, 25, 16, 30, 14, 12, 50, 20, 20, 20, 20};
            for (int i = 0; i < colWidths.length; i++) {
                sheet.setColumnWidth(i, colWidths[i] * 256);
            }

            sheet.createFreezePane(0, 1);

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                workbook.write(out);
                return out.toByteArray();
            }
        }
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
        if (status == null) return "";
        return switch (status) {
            case SENT      -> "Gönderildi";
            case DELIVERED -> "İletildi";
            case READ      -> "Okundu";
            case FAILED    -> "Başarısız";
        };
    }

    /**
     * Meta hata kodlarını Türkçe açıklamaya çevirir.
     */
    private String translateFailureReason(String code, String originalReason) {
        if (code == null || code.isBlank()) return "";
        return switch (code) {
            case "131026" -> "Mesaj iletilemedi (kullanıcı offline, WhatsApp aktif değil veya engagement düşük)";
            case "131047" -> "24 saat penceresi kapalı, tekrar template gerekli";
            case "131048" -> "Spam Rate limiti aşıldı (WABA korumalı)";
            case "131049" -> "Ekosistem koruması: çok mesaj atıldı veya kullanıcı engelledi";
            case "131050" -> "Kullanıcı sizi engelledi";
            case "131053" -> "Medya yüklenemedi (URL erişilemez)";
            case "131056" -> "Aynı kişiye çok mesaj (pair rate limit)";
            case "130472" -> "Kullanıcı pazarlama mesajını iptal etti (opt-out)";
            case "131000" -> "Genel sunucu hatası, tekrar denenmeli";
            default       -> originalReason != null ? originalReason : "Bilinmeyen hata kodu: " + code;
        };
    }
}
