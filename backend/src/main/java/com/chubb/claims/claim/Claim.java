package com.chubb.claims.claim;

import com.chubb.claims.common.BusinessRuleException;
import com.chubb.claims.common.InvalidStatusTransitionException;
import com.chubb.claims.common.Market;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A claim against a policy. All claim business rules live here:
 * creation rules ({@link #submit}), reserve limits ({@link #changeReserve}) and status changes ({@link #transitionTo}).
 * Which status changes are allowed is defined by {@link ClaimStatus}.
 */
@Entity
@Table(name = "claim")
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_number", nullable = false, unique = true, length = 30)
    private String claimNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    private Policy policy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 2)
    private Market market;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "incident_date", nullable = false)
    private LocalDate incidentDate;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(name = "estimated_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal estimatedAmount;

    @Column(name = "reserve_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal reserveAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClaimStatus status;

    @Column(name = "assigned_officer_id", length = 20)
    private String assignedOfficerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL)
    @OrderBy("changedAt ASC, id ASC")
    private List<ClaimStatusHistory> statusHistory = new ArrayList<>();

    protected Claim() {
        // for JPA
    }

    /**
     * Creates a new claim in status SUBMITTED.
     * Type, market and currency are copied from the policy; the reserve starts equal to the estimate.
     *
     * @param claimNumber  pre-generated claim number (see {@link ClaimNumberGenerator})
     * @param submittedBy  id of the user submitting the claim (recorded in the status history)
     */
    public static Claim submit(String claimNumber, Policy policy, LocalDate incidentDate, String description,
                               BigDecimal estimatedAmount, String submittedBy) {
        Objects.requireNonNull(claimNumber, "claimNumber");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(incidentDate, "incidentDate");
        Objects.requireNonNull(estimatedAmount, "estimatedAmount");
        Objects.requireNonNull(submittedBy, "submittedBy");

        if (description == null || description.isBlank()) {
            throw new BusinessRuleException("Description is required");
        }
        LocalDate today = policy.getMarket().today();
        if (incidentDate.isAfter(today)) {
            throw new BusinessRuleException(
                    "Incident date %s cannot be in the future (today in %s is %s)"
                            .formatted(incidentDate, policy.getMarket(), today));
        }
        if (estimatedAmount.signum() < 0) {
            throw new BusinessRuleException("Estimated amount cannot be negative");
        }

        Claim claim = new Claim();
        claim.claimNumber = claimNumber;
        claim.policy = policy;
        claim.type = policy.getProductType();
        claim.market = policy.getMarket();
        claim.currency = policy.getCurrency();
        claim.incidentDate = incidentDate;
        claim.description = description;
        claim.estimatedAmount = estimatedAmount;
        claim.changeReserve(estimatedAmount);
        claim.status = ClaimStatus.SUBMITTED;
        claim.createdAt = Instant.now();
        claim.updatedAt = claim.createdAt;
        claim.statusHistory.add(new ClaimStatusHistory(claim, null, ClaimStatus.SUBMITTED, submittedBy,
                "Claim submitted", claim.createdAt));
        return claim;
    }

    /** Sets the reserve. It must be between zero and the policy's coverage limit. */
    public void changeReserve(BigDecimal newReserve) {
        Objects.requireNonNull(newReserve, "newReserve");
        if (newReserve.signum() < 0) {
            throw new BusinessRuleException("Reserve amount cannot be negative");
        }
        if (newReserve.compareTo(policy.getCoverageLimit()) > 0) {
            throw new BusinessRuleException("Reserve amount %s %s exceeds the policy coverage limit of %s %s"
                    .formatted(currency, newReserve.toPlainString(),
                            currency, policy.getCoverageLimit().toPlainString()));
        }
        this.reserveAmount = newReserve;
        this.updatedAt = Instant.now();
    }

    /**
     * Moves the claim to a new status if {@link ClaimStatus} allows it, and records the change in the history.
     *
     * @throws InvalidStatusTransitionException if the move is not allowed from the current status
     */
    public void transitionTo(ClaimStatus newStatus, String changedBy, String reason) {
        Objects.requireNonNull(newStatus, "newStatus");
        Objects.requireNonNull(changedBy, "changedBy");
        if (!status.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException(claimNumber, status, newStatus, status.allowedNextStatuses());
        }
        Instant now = Instant.now();
        statusHistory.add(new ClaimStatusHistory(this, status, newStatus, changedBy, reason, now));
        this.status = newStatus;
        this.updatedAt = now;
    }

    public boolean isOpen() {
        return !status.isFinal();
    }

    public Long getId() {
        return id;
    }

    public String getClaimNumber() {
        return claimNumber;
    }

    public Policy getPolicy() {
        return policy;
    }

    public ProductType getType() {
        return type;
    }

    public Market getMarket() {
        return market;
    }

    public String getCurrency() {
        return currency;
    }

    public LocalDate getIncidentDate() {
        return incidentDate;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getEstimatedAmount() {
        return estimatedAmount;
    }

    public BigDecimal getReserveAmount() {
        return reserveAmount;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public String getAssignedOfficerId() {
        return assignedOfficerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public List<ClaimStatusHistory> getStatusHistory() {
        return Collections.unmodifiableList(statusHistory);
    }
}
