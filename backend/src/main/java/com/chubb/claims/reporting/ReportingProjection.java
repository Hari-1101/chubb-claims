package com.chubb.claims.reporting;

import com.chubb.claims.common.ClaimEvent;
import com.chubb.claims.common.ProcessedEventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Keeps the reporting read model (reporting_claim) up to date from claim events. Two safety nets:
 * <ul>
 *   <li>processed_event: the same event (same eventId) is applied at most once;</li>
 *   <li>claimVersion: a snapshot that isn't newer than the row's lastClaimVersion is ignored, so out-of-order
 *       delivery can't overwrite newer data with older data.</li>
 * </ul>
 */
@Service
public class ReportingProjection {

    public static final String CONSUMER_GROUP = "claims-reporting";

    private static final Logger log = LoggerFactory.getLogger(ReportingProjection.class);

    private final ReportingClaimRepository repository;
    private final ProcessedEventStore processedEvents;

    public ReportingProjection(ReportingClaimRepository repository, ProcessedEventStore processedEvents) {
        this.repository = repository;
        this.processedEvents = processedEvents;
    }

    @Transactional
    public void handle(ClaimEvent event) {
        if (!processedEvents.markProcessed(event.eventId(), CONSUMER_GROUP)) {
            log.info("Skipping duplicate event {} ({} for {})", event.eventId(), event.eventType(), event.claimNumber());
            return;
        }
        ReportingClaim row = repository.findById(event.claimNumber())
                .orElseGet(() -> new ReportingClaim(event.claimNumber()));
        if (!row.apply(event, Instant.now())) {
            log.info("Ignoring stale event {} for {}: version {} <= {}", event.eventType(), event.claimNumber(),
                    event.claimVersion(), row.getLastClaimVersion());
            return;
        }
        repository.save(row);
    }
}
