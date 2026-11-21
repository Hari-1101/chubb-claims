package com.chubb.claims.reporting;

import com.chubb.claims.common.ClaimEvent;
import com.chubb.claims.common.ClaimEventType;
import com.chubb.claims.common.Market;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Reporting's own copy of a claim, built only from claim events (one row per claim).
 * It never reads the claim module's tables.
 */
@Entity
@Table(name = "reporting_claim")
public class ReportingClaim {

    /** Statuses after which a claim no longer counts towards exposure or workload (part of the event contract). */
    static final List<String> CLOSED_STATUSES = List.of("SETTLED", "REJECTED");

    @Id
    @Column(name = "claim_number", length = 30)
    private String claimNumber;

    @Column(nullable = false, length = 20)
    private String status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 2)
    private Market market;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(name = "reserve_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal reserveAmount;

    @Column(name = "assigned_officer_id", length = 20)
    private String assignedOfficerId;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    /** -1 for a row that hasn't applied any event yet, so version 0 (ClaimSubmitted) is accepted. */
    @Column(name = "last_claim_version", nullable = false)
    private long lastClaimVersion = -1;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ReportingClaim() {
        // for JPA
    }

    ReportingClaim(String claimNumber) {
        this.claimNumber = claimNumber;
    }

    /**
     * Applies the event's claim snapshot if it is newer than what we already have.
     *
     * @return false if the event was ignored because its claimVersion is not newer (duplicate or out of order)
     */
    boolean apply(ClaimEvent event, Instant now) {
        if (event.claimVersion() <= lastClaimVersion) {
            return false;
        }
        status = event.status();
        market = event.market();
        currency = event.currency();
        type = event.type();
        reserveAmount = event.reserveAmount();
        assignedOfficerId = event.assignedOfficerId();
        submittedAt = event.submittedAt();
        if (event.eventType() == ClaimEventType.ClaimApproved || event.eventType() == ClaimEventType.ClaimRejected) {
            decidedAt = event.occurredAt();
        }
        lastClaimVersion = event.claimVersion();
        updatedAt = now;
        return true;
    }

    boolean isOpen() {
        return !CLOSED_STATUSES.contains(status);
    }

    public String getClaimNumber() {
        return claimNumber;
    }

    public String getStatus() {
        return status;
    }

    public Market getMarket() {
        return market;
    }

    public String getCurrency() {
        return currency;
    }

    public String getType() {
        return type;
    }

    public BigDecimal getReserveAmount() {
        return reserveAmount;
    }

    public String getAssignedOfficerId() {
        return assignedOfficerId;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public long getLastClaimVersion() {
        return lastClaimVersion;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
