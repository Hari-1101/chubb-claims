package com.chubb.claims.claim;

import com.chubb.claims.claim.ClaimDetailResponse.InformationRequestResponse;
import com.chubb.claims.claim.ClaimDetailResponse.StatusHistoryResponse;

import java.math.BigDecimal;
import java.util.List;

/** Entity → response DTO. Called inside service transactions, so lazy associations can be read. */
final class ClaimMapper {

    private ClaimMapper() {
    }

    static PolicyResponse toResponse(Policy p) {
        return new PolicyResponse(p.getPolicyNumber(), p.getMarket(), p.getProductType(), p.getCurrency(),
                p.getCoverageLimit());
    }

    static ClaimSummaryResponse toSummary(Claim c, boolean showReserve) {
        return new ClaimSummaryResponse(c.getClaimNumber(), c.getPolicy().getPolicyNumber(), c.getType(),
                c.getMarket(), c.getCurrency(), c.getIncidentDate(), c.getEstimatedAmount(), reserve(c, showReserve),
                c.getStatus(), c.getAssignedOfficerId(), c.getCreatedAt(), c.getUpdatedAt());
    }

    static ClaimDetailResponse toDetail(Claim c, List<InformationRequest> requests, boolean showReserve) {
        return new ClaimDetailResponse(c.getClaimNumber(), c.getPolicy().getPolicyNumber(), c.getType(),
                c.getMarket(), c.getCurrency(), c.getIncidentDate(), c.getDescription(), c.getEstimatedAmount(),
                reserve(c, showReserve), c.getStatus(), c.getAssignedOfficerId(), c.getCreatedAt(), c.getUpdatedAt(),
                c.getStatusHistory().stream()
                        .map(h -> new StatusHistoryResponse(h.getFromStatus(), h.getToStatus(), h.getChangedBy(),
                                h.getReason(), h.getChangedAt()))
                        .toList(),
                requests.stream()
                        .map(r -> new InformationRequestResponse(r.getId(), r.getQuestion(), r.getResponse(),
                                r.getRequestedBy(), r.getRequestedAt(), r.getRespondedAt()))
                        .toList());
    }

    /** Null means "omit": the reserve is an internal figure that claimants don't see. */
    private static BigDecimal reserve(Claim c, boolean showReserve) {
        return showReserve ? c.getReserveAmount() : null;
    }
}
