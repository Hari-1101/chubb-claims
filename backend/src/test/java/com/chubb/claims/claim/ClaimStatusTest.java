package com.chubb.claims.claim;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.chubb.claims.claim.ClaimStatus.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plain unit test: no Spring, no database. */
class ClaimStatusTest {

    /** The agreed lifecycle, written out independently of the production code. */
    static final Map<ClaimStatus, Set<ClaimStatus>> EXPECTED = Map.of(
            SUBMITTED, EnumSet.of(UNDER_REVIEW),
            UNDER_REVIEW, EnumSet.of(INFO_REQUESTED, APPROVED, REJECTED),
            INFO_REQUESTED, EnumSet.of(UNDER_REVIEW),
            APPROVED, EnumSet.of(SETTLED),
            SETTLED, EnumSet.noneOf(ClaimStatus.class),
            REJECTED, EnumSet.noneOf(ClaimStatus.class));

    @Test
    void everyStatusHasExactlyTheAgreedNextStatuses() {
        for (ClaimStatus status : ClaimStatus.values()) {
            assertEquals(EXPECTED.get(status), status.allowedNextStatuses(), "next statuses of " + status);
        }
    }

    @Test
    void canTransitionToMatchesTheAgreedLifecycleForEveryPair() {
        for (ClaimStatus from : ClaimStatus.values()) {
            for (ClaimStatus to : ClaimStatus.values()) {
                assertEquals(EXPECTED.get(from).contains(to), from.canTransitionTo(to), from + " -> " + to);
            }
        }
    }

    @Test
    void onlySettledAndRejectedAreFinal() {
        assertTrue(SETTLED.isFinal());
        assertTrue(REJECTED.isFinal());
        for (ClaimStatus status : EnumSet.complementOf(EnumSet.of(SETTLED, REJECTED))) {
            assertFalse(status.isFinal(), status + " should not be final");
        }
    }
}
