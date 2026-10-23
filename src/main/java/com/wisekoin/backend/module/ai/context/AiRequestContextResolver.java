package com.wisekoin.backend.module.ai.context;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AiRequestContextResolver {
    private final Locale defaultLocale;
    private final ZoneId defaultZoneId;

    public AiRequestContextResolver(
            Clock clock, @Value("${app.ai.default-locale:vi-VN}") String defaultLocaleTag) {
        this.defaultLocale = normalizeLocale(defaultLocaleTag, Locale.forLanguageTag("vi-VN"));
        this.defaultZoneId = clock.getZone();
    }

    public AiRequestContext resolve(String localeTag, String timeZone) {
        return new AiRequestContext(
                normalizeLocale(localeTag, defaultLocale), normalizeZone(timeZone));
    }

    private Locale normalizeLocale(String localeTag, Locale fallback) {
        if (localeTag == null || localeTag.isBlank()) {
            return fallback;
        }
        if (localeTag.length() > 35) {
            return fallback;
        }
        Locale locale = Locale.forLanguageTag(localeTag.trim());
        return locale.getLanguage().isBlank() ? fallback : locale;
    }

    private ZoneId normalizeZone(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) {
            return defaultZoneId;
        }
        if (timeZone.length() > 100) {
            return defaultZoneId;
        }
        try {
            return ZoneId.of(timeZone.trim());
        } catch (DateTimeException exception) {
            return defaultZoneId;
        }
    }
}
