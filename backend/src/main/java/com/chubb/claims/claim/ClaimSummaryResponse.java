package com.chubb.claims.claim;

import com.chubb.claims.common.Market;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** One row in a claim list (claimant's "my claims" or the staff queue). */
public record ClaimSummaryResponse(
        String claimNumber,
        String policyNumber,
        ProductType type,
        Market market,
        String currency,
        LocalDate incidentDate,
        BigDecimal estimatedAmount,
        BigDecimal reserveAmount,
        ClaimStatus status,
        String assignedOfficerId,
        Instant createdAt,
        Instant updatedAt) {
}
