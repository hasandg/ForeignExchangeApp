package com.hasandag.exchange.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enum representing supported currencies in the foreign exchange application.
 * Contains the most commonly traded currencies.
 */
public enum Currency {
    USD("USD", "US Dollar"),
    EUR("EUR", "Euro"),
    GBP("GBP", "British Pound"),
    JPY("JPY", "Japanese Yen"),
    CHF("CHF", "Swiss Franc"),
    CAD("CAD", "Canadian Dollar"),
    AUD("AUD", "Australian Dollar"),
    CNY("CNY", "Chinese Yuan"),
    SEK("SEK", "Swedish Krona"),
    NOK("NOK", "Norwegian Krone");

    private final String code;
    private final String displayName;

    Currency(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    @JsonCreator
    public static Currency fromCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("Currency code cannot be null");
        }
        
        String upperCode = code.toUpperCase().trim();
        for (Currency currency : Currency.values()) {
            if (currency.code.equals(upperCode)) {
                return currency;
            }
        }
        throw new IllegalArgumentException("Unsupported currency code: " + code);
    }

    @Override
    public String toString() {
        return code;
    }
} 