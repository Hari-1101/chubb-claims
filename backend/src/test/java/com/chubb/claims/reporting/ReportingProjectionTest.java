package com.chubb.claims.reporting;

import com.chubb.claims.common.ClaimEvent;
import com.chubb.claims.common.ClaimEventType;
import com.chubb.claims.common.Market;
import com.chubb.claims.common.ProcessedEventStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportingProjectionTest {

    @Mock ReportingClaimRepository repository;
    @Mock ProcessedEventStore processedEvents;
    @InjectMocks ReportingProjection projection;

    static final String CLAIM = "CLM-HK-2026-000010";

    static ClaimEvent event(ClaimEventType type, long version, String status, String reserve, String officer) {
        return new ClaimEvent(UUID.randomUUID(), type, Instant.now(), CLAIM, version, status, Market.HK, "HKD",
                "PROPERTY", new BigDecimal(reserve), officer, "C003", Instant.parse("2026-11-20T02:00:00Z"));
    }

    /** A row that has already applied version 5 (UNDER_REVIEW, reserve 50,000, officer S003). */
    static ReportingClaim rowAtVersion5() {
        ReportingClaim row = new ReportingClaim(CLAIM);
        row.apply(event(ClaimEventType.ReserveChanged, 5, "UNDER_REVIEW", "50000.00", "S003"), Instant.now());
        return row;
    }

    @Test
    void firstEventCreatesTheRow() {
        when(processedEvents.markProcessed(any(), any())).thenReturn(true);
        when(repository.findById(CLAIM)).thenReturn(Optional.empty());

        projection.handle(event(ClaimEventType.ClaimSubmitted, 0, "SUBMITTED", "80000.00", null));

        verify(repository).save(any(ReportingClaim.class));
    }

    @Test
    void olderOrEqualClaimVersionIsIgnored() {
        ReportingClaim row = rowAtVersion5();
        when(processedEvents.markProcessed(any(), any())).thenReturn(true);
        when(repository.findById(CLAIM)).thenReturn(Optional.of(row));

        // Out-of-order: an older snapshot arrives after a newer one.
        projection.handle(event(ClaimEventType.ClaimAssigned, 3, "UNDER_REVIEW", "80000.00", "S003"));
        // Same version again (e.g. replayed with a new eventId).
        projection.handle(event(ClaimEventType.ReserveChanged, 5, "UNDER_REVIEW", "99999.00", "S003"));

        assertEquals(5, row.getLastClaimVersion());
        assertEquals(new BigDecimal("50000.00"), row.getReserveAmount(), "newer data not overwritten");
        verify(repository, never()).save(any());
    }

    @Test
    void newerClaimVersionIsApplied() {
        ReportingClaim row = rowAtVersion5();
        when(processedEvents.markProcessed(any(), any())).thenReturn(true);
        when(repository.findById(CLAIM)).thenReturn(Optional.of(row));

        projection.handle(event(ClaimEventType.ClaimApproved, 6, "APPROVED", "50000.00", "S003"));

        assertEquals(6, row.getLastClaimVersion());
        assertEquals("APPROVED", row.getStatus());
        assertNotNull(row.getDecidedAt(), "approval sets decidedAt");
        verify(repository).save(row);
    }

    @Test
    void sameEventDeliveredTwiceIsAppliedOnce() {
        ClaimEvent event = event(ClaimEventType.ClaimSubmitted, 0, "SUBMITTED", "80000.00", null);
        when(processedEvents.markProcessed(event.eventId(), ReportingProjection.CONSUMER_GROUP)).thenReturn(true, false);
        when(repository.findById(CLAIM)).thenReturn(Optional.empty());

        projection.handle(event);
        projection.handle(event);

        verify(repository, times(1)).findById(CLAIM);
        verify(repository, times(1)).save(any());
    }

    @Test
    void decidedAtIsOnlySetByApprovalOrRejection() {
        ReportingClaim row = new ReportingClaim(CLAIM);
        row.apply(event(ClaimEventType.ClaimSubmitted, 0, "SUBMITTED", "100.00", null), Instant.now());
        row.apply(event(ClaimEventType.ClaimAssigned, 1, "UNDER_REVIEW", "100.00", "S003"), Instant.now());
        assertNull(row.getDecidedAt());

        row.apply(event(ClaimEventType.ClaimRejected, 2, "REJECTED", "100.00", "S003"), Instant.now());
        Instant decided = row.getDecidedAt();
        assertNotNull(decided);
    }
}
