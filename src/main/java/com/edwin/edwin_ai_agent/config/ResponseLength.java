package com.edwin.edwin_ai_agent.config;

import java.util.Locale;

/**
 * Shared response length modes exposed through the HTTP API.
 */
public enum ResponseLength {

    SHORT("short"),
    MEDIUM("medium"),
    LONG("long");

    private final String wireValue;

    ResponseLength(String wireValue) {
        this.wireValue = wireValue;
    }

    public String getWireValue() {
        return wireValue;
    }

    public static ResponseLength fromWireValue(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return MEDIUM;
        }

        String normalizedValue = rawValue.trim().toLowerCase(Locale.ROOT);
        for (ResponseLength responseLength : values()) {
            if (responseLength.wireValue.equals(normalizedValue)) {
                return responseLength;
            }
        }
        return MEDIUM;
    }
}
