package com.chubb.claims.common;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * The six APAC markets we operate in. Each market has exactly one currency, and a reference time zone used to
 * decide what "today" means for that market (e.g. an incident date cannot be in the future).
 */
public enum Market {
    SG("SGD", "Asia/Singapore"),
    MY("MYR", "Asia/Kuala_Lumpur"),
    HK("HKD", "Asia/Hong_Kong"),
    TH("THB", "Asia/Bangkok"),
    ID("IDR", "Asia/Jakarta"),
    AU("AUD", "Australia/Sydney");

    private final String currency;
    private final ZoneId zone;

    Market(String currency, String zone) {
        this.currency = currency;
        this.zone = ZoneId.of(zone);
    }

    /** ISO 4217 currency code, e.g. "SGD". */
    public String currency() {
        return currency;
    }

    public ZoneId zone() {
        return zone;
    }

    /** Today's date in this market. */
    public LocalDate today() {
        return LocalDate.now(zone);
    }
}
