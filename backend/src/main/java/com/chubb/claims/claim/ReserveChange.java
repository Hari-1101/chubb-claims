package com.chubb.claims.claim;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** Audit record of one reserve change. Created only by {@link Claim#changeReserve}; never modified. */
@Entity
@Table(name = "reserve_change")
public class ReserveChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    @Column(name = "old_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal oldAmount;

    @Column(name = "new_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal newAmount;

    @Column(name = "changed_by", nullable = false, length = 20)
    private String changedBy;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected ReserveChange() {
        // for JPA
    }

    ReserveChange(Claim claim, BigDecimal oldAmount, BigDecimal newAmount, String changedBy, String reason,
                  Instant changedAt) {
        this.claim = claim;
        this.oldAmount = oldAmount;
        this.newAmount = newAmount;
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

    public BigDecimal getOldAmount() {
        return oldAmount;
    }

    public BigDecimal getNewAmount() {
        return newAmount;
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
