package com.ihh.wpBot.service;

import java.util.List;
import java.util.Map;

public final class FailureCategoryMapper {

    private FailureCategoryMapper() {
    }

    public static String getCategory(String code) {
        if (code == null) {
            return "";
        }
        return switch (code) {
            case "131026" -> "Ulaşılamadı";
            case "131047" -> "Pencere Kapalı";
            case "131048" -> "Spam Koruma";
            case "131049" -> "Ekosistem Koruma";
            case "131050" -> "Engellenmiş";
            case "131053" -> "Medya Hatası";
            case "131056" -> "Hız Sınırı";
            case "130472" -> "Pazarlama İptali";
            case "131000" -> "Sunucu Hatası";
            default -> "Diğer Hata";
        };
    }

    public static String getDetail(String code, String originalReason) {
        if (code == null || code.isBlank()) {
            return "";
        }
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
            default -> originalReason != null && !originalReason.isBlank()
                    ? originalReason
                    : "Bilinmeyen hata kodu: " + code;
        };
    }

    public static List<Map<String, String>> getAllCategories() {
        return List.of(
                Map.of("code", "131026", "category", "Ulaşılamadı"),
                Map.of("code", "131047", "category", "Pencere Kapalı"),
                Map.of("code", "131048", "category", "Spam Koruma"),
                Map.of("code", "131049", "category", "Ekosistem Koruma"),
                Map.of("code", "131050", "category", "Engellenmiş"),
                Map.of("code", "131053", "category", "Medya Hatası"),
                Map.of("code", "131056", "category", "Hız Sınırı"),
                Map.of("code", "130472", "category", "Pazarlama İptali"),
                Map.of("code", "131000", "category", "Sunucu Hatası")
        );
    }
}