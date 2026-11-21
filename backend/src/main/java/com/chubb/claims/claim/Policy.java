package com.chubb.claims.claim;

import com.chubb.claims.common.AppUser;
import com.chubb.claims.common.BusinessRuleException;
import com.chubb.claims.common.Market;
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

import java.math.BigDecimal;
import java.util.Objects;

/** An insurance policy held by a claimant. Seeded; not created through the API. */
@Entity
@Table(name = "policy")
public class Policy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_number", nullable = false, unique = true, length = 30)
    private String policyNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "holder_id", nullable = false)
    private AppUser holder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 2)
    private Market market;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 20)
    private ProductType productType;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "coverage_limit", nullable = false, precision = 15, scale = 2)
    private BigDecimal coverageLimit;

    protected Policy() {
        // for JPA
    }

    /** Currency always follows the market, so it can't be set inconsistently. */
    public Policy(String policyNumber, AppUser holder, Market market, ProductType productType, BigDecimal coverageLimit) {
        this.policyNumber = Objects.requireNonNull(policyNumber, "policyNumber");
        this.holder = Objects.requireNonNull(holder, "holder");
        this.market = Objects.requireNonNull(market, "market");
        this.productType = Objects.requireNonNull(productType, "productType");
        this.coverageLimit = Objects.requireNonNull(coverageLimit, "coverageLimit");
        if (!holder.isClaimant()) {
            throw new BusinessRuleException("Policy holder " + holder.getId() + " must be a claimant");
        }
        if (coverageLimit.signum() <= 0) {
            throw new BusinessRuleException("Coverage limit must be greater than zero");
        }
        this.currency = market.currency();
    }

    public Long getId() {
        return id;
    }

    public String getPolicyNumber() {
        return policyNumber;
    }

    public AppUser getHolder() {
        return holder;
    }

    public Market getMarket() {
        return market;
    }

    public ProductType getProductType() {
        return productType;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getCoverageLimit() {
        return coverageLimit;
    }
}
