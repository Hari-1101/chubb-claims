package com.chubb.claims.common;

import java.util.Collection;

/** Thrown when a claim is asked to move to a status that its current status does not allow. */
public class InvalidStatusTransitionException extends BusinessRuleException {

    private final String claimNumber;
    private final String fromStatus;
    private final String toStatus;

    public InvalidStatusTransitionException(String claimNumber, Enum<?> from, Enum<?> to, Collection<? extends Enum<?>> allowed) {
        super("Cannot move claim %s from %s to %s. Allowed next statuses: %s."
                .formatted(claimNumber, from, to, allowed.isEmpty() ? "none (" + from + " is final)" : allowed));
        this.claimNumber = claimNumber;
        this.fromStatus = from.name();
        this.toStatus = to.name();
    }

    public String getClaimNumber() {
        return claimNumber;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }
}
