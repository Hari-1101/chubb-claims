package com.chubb.claims.reporting;

import com.chubb.claims.common.Market;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Liability exposure: open claims and total reserve per market AND currency, with a breakdown by claim type.
 * There is deliberately no grand total: amounts in different currencies are never added together.
 *
 * @param lastUpdated when the reporting read model last changed (reports may lag a moment behind the claims)
 */
public record ExposureReport(Instant lastUpdated, List<MarketExposure> exposures) {

    public record MarketExposure(Market market, String currency, long openClaims, BigDecimal totalReserve,
                                 List<TypeExposure> byType) {
    }

    public record TypeExposure(String type, long openClaims, BigDecimal totalReserve) {
    }

    /** Groups aggregate rows by (market, currency); totals are only summed inside one group, i.e. one currency. */
    static ExposureReport from(List<ExposureRow> rows, Instant lastUpdated) {
        record MarketCurrency(Market market, String currency) {
        }
        Map<MarketCurrency, List<ExposureRow>> groups = new LinkedHashMap<>();
        rows.stream()
                .sorted(Comparator.comparing(ExposureRow::market).thenComparing(ExposureRow::currency)
                        .thenComparing(ExposureRow::type))
                .forEach(row -> groups.computeIfAbsent(new MarketCurrency(row.market(), row.currency()),
                        key -> new ArrayList<>()).add(row));

        List<MarketExposure> exposures = groups.entrySet().stream()
                .map(group -> {
                    List<ExposureRow> sameCurrency = group.getValue();
                    return new MarketExposure(
                            group.getKey().market(),
                            group.getKey().currency(),
                            sameCurrency.stream().mapToLong(ExposureRow::openClaims).sum(),
                            sameCurrency.stream().map(ExposureRow::totalReserve).reduce(BigDecimal.ZERO, BigDecimal::add),
                            sameCurrency.stream()
                                    .map(r -> new TypeExposure(r.type(), r.openClaims(), r.totalReserve()))
                                    .toList());
                })
                .toList();
        return new ExposureReport(lastUpdated, exposures);
    }
}
