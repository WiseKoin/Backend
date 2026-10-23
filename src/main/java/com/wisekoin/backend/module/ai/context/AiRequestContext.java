package com.wisekoin.backend.module.ai.context;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;

public record AiRequestContext(Locale locale, ZoneId zoneId) {
    public String localeTag() {
        return locale.toLanguageTag();
    }

    public String timeZone() {
        return zoneId.getId();
    }

    public LocalDateTime now(Clock clock) {
        return LocalDateTime.ofInstant(clock.instant(), zoneId);
    }
}
