package com.wisekoin.backend.module.ai.notification;

import com.wisekoin.backend.module.common.exception.BadRequestException;
import java.util.Arrays;

public enum BankNotificationSource {
    VCB("VCB Digibank", "vcb-v1"),
    MB("MB Bank", "mb-v1"),
    TECHCOMBANK("Techcombank Mobile", "techcombank-v1"),
    MOMO("MoMo", "momo-v1");

    private final String displayName;
    private final String parserVersion;

    BankNotificationSource(String displayName, String parserVersion) {
        this.displayName = displayName;
        this.parserVersion = parserVersion;
    }

    public String displayName() {
        return displayName;
    }

    public String parserVersion() {
        return parserVersion;
    }

    public void validateParserVersion(String value) {
        if (!parserVersion.equals(value)) {
            throw new BadRequestException("Unsupported parser version for notification source");
        }
    }

    public static BankNotificationSource from(String value) {
        return Arrays.stream(values())
                .filter(source -> source.name().equals(value))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Unsupported notification source"));
    }
}
