package com.ihh.wpBot.model;

import jakarta.persistence.*;
import java.time.LocalTime;

@Entity
@Table(name = "auto_reply_settings")
public class AutoReplySettings {

    /** Singleton satır — id her zaman 1. */
    @Id
    private Long id = 1L;

    @Column(nullable = false)
    private Boolean enabled = true;

    /** true → sadece workingHoursStart–End arasında çalışır */
    @Column(nullable = false)
    private Boolean useWorkingHours = false;

    private LocalTime workingHoursStart = LocalTime.of(9, 0);
    private LocalTime workingHoursEnd   = LocalTime.of(18, 0);

    @Column(length = 2000)
    private String outOfHoursMessage =
            "🌙 Mesai saatleri dışındasınız. Mesajınız alındı, en kısa sürede dönüş yapacağız.\n\n" +
            "Mesai: 09:00-18:00\n" +
            "İletişim: 0536 322 83 03";

    /** Aynı kişiye otomatik cevap gönderim arası (saniye). Spam önler. */
    @Column(nullable = false)
    private Integer cooldownSeconds = 60;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Boolean getUseWorkingHours() { return useWorkingHours; }
    public void setUseWorkingHours(Boolean useWorkingHours) { this.useWorkingHours = useWorkingHours; }

    public LocalTime getWorkingHoursStart() { return workingHoursStart; }
    public void setWorkingHoursStart(LocalTime workingHoursStart) { this.workingHoursStart = workingHoursStart; }

    public LocalTime getWorkingHoursEnd() { return workingHoursEnd; }
    public void setWorkingHoursEnd(LocalTime workingHoursEnd) { this.workingHoursEnd = workingHoursEnd; }

    public String getOutOfHoursMessage() { return outOfHoursMessage; }
    public void setOutOfHoursMessage(String outOfHoursMessage) { this.outOfHoursMessage = outOfHoursMessage; }

    public Integer getCooldownSeconds() { return cooldownSeconds; }
    public void setCooldownSeconds(Integer cooldownSeconds) { this.cooldownSeconds = cooldownSeconds; }
}
