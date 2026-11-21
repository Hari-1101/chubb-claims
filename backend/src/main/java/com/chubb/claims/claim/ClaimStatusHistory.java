package com.chubb.claims.claim;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** One status change of a claim. Created only by {@link Claim}; never modified. */
@Entity
@Table(name = "claim_status_history")
public class ClaimStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    /** Null for the initial SUBMITTED entry. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private ClaimStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private ClaimStatus toStatus;

    @Column(name = "changed_by", nullable = false, length = 20)
    private String changedBy;

    @Column(length = 500)
    private String reason;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected ClaimStatusHistory() {
        // for JPA
    }

    ClaimStatusHistory(Claim claim, ClaimStatus fromStatus, ClaimStatus toStatus, String changedBy, String reason,
                       Instant changedAt) {
        this.claim = claim;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedBy = changedBy;
        this.reason = reason;
        this.changedAt = changedAt;
    }

    public Long getId() {
        return id;
    }

    public Claim getClaim() {
        return claim;
    }

    public ClaimStatus getFromStatus() {
        return fromStatus;
    }

    public ClaimStatus getToStatus() {
        return toStatus;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public String getReason() {
        return reason;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
