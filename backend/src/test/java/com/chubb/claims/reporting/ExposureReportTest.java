package com.chubb.claims.reporting;

import com.chubb.claims.common.Market;
import com.chubb.claims.reporting.ExposureReport.MarketExposure;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExposureReportTest {

    static ExposureRow row(Market market, String currency, String type, long count, String total) {
        return new ExposureRow(market, currency, type, count, new BigDecimal(total));
    }

    @Test
    void totalsAreGroupedByMarketAndCurrencyAndNeverMixed() {
        List<ExposureRow> rows = List.of(
                row(Market.SG, "SGD", "MOTOR", 2, "30000.00"),
                row(Market.SG, "SGD", "PROPERTY", 1, "200000.00"),
                row(Market.AU, "AUD", "MOTOR", 3, "45000.00"),
                row(Market.ID, "IDR", "PROPERTY", 1, "750000000.00"),
                // Not possible with today's data (one currency per market), but must still never be merged:
                row(Market.SG, "USD", "MOTOR", 1, "999.00"));

        ExposureReport report = ExposureReport.from(rows, Instant.now());

        assertEquals(4, report.exposures().size(), "one entry per market + currency");
        MarketExposure sgd = find(report, Market.SG, "SGD");
        assertEquals(3, sgd.openClaims());
        assertEquals(new BigDecimal("230000.00"), sgd.totalReserve());
        assertEquals(2, sgd.byType().size());

        MarketExposure usd = find(report, Market.SG, "USD");
        assertEquals(new BigDecimal("999.00"), usd.totalReserve(), "USD kept separate from SGD");

        assertEquals(new BigDecimal("750000000.00"), find(report, Market.ID, "IDR").totalReserve());
        // Every byType entry belongs to its parent's currency, so per-type sums equal the parent total.
        for (MarketExposure e : report.exposures()) {
            BigDecimal typeSum = e.byType().stream().map(ExposureReport.TypeExposure::totalReserve)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(e.totalReserve(), typeSum);
        }
    }

    @Test
    void emptyReadModelGivesEmptyReport() {
        ExposureReport report = ExposureReport.from(List.of(), null);
        assertEquals(0, report.exposures().size());
    }

    private static MarketExposure find(ExposureReport report, Market market, String currency) {
        return report.exposures().stream()
                .filter(e -> e.market() == market && e.currency().equals(currency))
                .findFirst().orElseThrow();
    }
}
