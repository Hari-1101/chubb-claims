package com.chubb.claims.claim;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Claim lifecycle. This enum is the ONE place that defines which status changes are allowed.
 *
 * <pre>
 * SUBMITTED      -> UNDER_REVIEW
 * UNDER_REVIEW   -> INFO_REQUESTED | APPROVED | REJECTED
 * INFO_REQUESTED -> UNDER_REVIEW
 * APPROVED       -> SETTLED
 * SETTLED, REJECTED: final
 * </pre>
 */
public enum ClaimStatus {
    SUBMITTED,
    UNDER_REVIEW,
    INFO_REQUESTED,
    APPROVED,
    SETTLED,
    REJECTED;

    public Set<ClaimStatus> allowedNextStatuses() {
        Set<ClaimStatus> next = switch (this) {
            case SUBMITTED -> EnumSet.of(UNDER_REVIEW);
            case UNDER_REVIEW -> EnumSet.of(INFO_REQUESTED, APPROVED, REJECTED);
            case INFO_REQUESTED -> EnumSet.of(UNDER_REVIEW);
            case APPROVED -> EnumSet.of(SETTLED);
            case SETTLED, REJECTED -> EnumSet.noneOf(ClaimStatus.class);
        };
        return Collections.unmodifiableSet(next);
    }

    public boolean canTransitionTo(ClaimStatus next) {
        return allowedNextStatuses().contains(next);
    }

    /** Final statuses can't move anywhere. Claims in a non-final status are "open" (count towards exposure). */
    public boolean isFinal() {
        return allowedNextStatuses().isEmpty();
    }
}
