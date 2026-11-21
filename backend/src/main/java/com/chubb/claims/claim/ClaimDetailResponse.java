package com.chubb.claims.claim;

import com.chubb.claims.common.Market;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Full claim view including its status history and information requests. */
public record ClaimDetailResponse(
        String claimNumber,
        String policyNumber,
        ProductType type,
        Market market,
        String currency,
        LocalDate incidentDate,
        String description,
        BigDecimal estimatedAmount,
        BigDecimal reserveAmount,
        ClaimStatus status,
        String assignedOfficerId,
        Instant createdAt,
        Instant updatedAt,
        List<StatusHistoryResponse> statusHistory,
        List<InformationRequestResponse> informationRequests) {

    public record StatusHistoryResponse(ClaimStatus fromStatus, ClaimStatus toStatus, String changedBy, String reason,
                                        Instant changedAt) {
    }

    public record InformationRequestResponse(Long id, String question, String response, String requestedBy,
                                             Instant requestedAt, Instant respondedAt) {
    }
}
