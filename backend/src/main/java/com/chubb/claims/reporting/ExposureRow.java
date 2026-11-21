package com.chubb.claims.reporting;

import com.chubb.claims.common.Market;

import java.math.BigDecimal;

/** One aggregate row from the read model: open claims for one market + currency + type. */
public record ExposureRow(Market market, String currency, String type, Long openClaims, BigDecimal totalReserve) {
}
