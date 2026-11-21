package com.chubb.claims.claim;

import com.chubb.claims.common.Market;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

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
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "Staff only; omitted for claimants")
        BigDecimal reserveAmount,
        ClaimStatus status,
        String assignedOfficerId,
        Instant createdAt,
        Instant updatedAt) {
}
