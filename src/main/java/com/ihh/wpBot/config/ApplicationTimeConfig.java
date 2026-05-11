package com.ihh.wpBot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;

@Configuration
public class ApplicationTimeConfig {

    @Bean
    public ZoneId applicationZoneId(@Value("${app.timezone:Europe/Istanbul}") String zoneId) {
        return ZoneId.of(zoneId);
    }
}
