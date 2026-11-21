package com.chubb.claims.claim;

import com.chubb.claims.common.CurrentUser;
import com.chubb.claims.common.ForbiddenException;
import com.chubb.claims.common.Market;
import com.chubb.claims.common.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Claims-staff use cases. Access rule: once a claim is assigned, only the assigned officer can act on it.
 * Whether a status change is allowed is decided by the domain ({@link Claim} / {@link ClaimStatus}), not here.
 */
@Service
@Transactional
public class StaffClaimService {

    private static final Logger log = LoggerFactory.getLogger(StaffClaimService.class);

    /** Oldest first so nothing waits too long; id breaks ties. */
    private static final Sort QUEUE_ORDER = Sort.by("createdAt").ascending().and(Sort.by("id").ascending());

    private final ClaimRepository claimRepository;
    private final InformationRequestRepository informationRequestRepository;

    public StaffClaimService(ClaimRepository claimRepository, InformationRequestRepository informationRequestRepository) {
        this.claimRepository = claimRepository;
        this.informationRequestRepository = informationRequestRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<ClaimSummaryResponse> queue(CurrentUser user, ClaimStatus status, Market market,
                                                    ProductType type, AssignmentFilter assigned, int page, int size) {
        var filters = ClaimQueueFilters.of(status, market, type, assigned, user.id());
        return PageResponse.of(claimRepository.findAll(filters, PageRequest.of(page, size, QUEUE_ORDER)),
                ClaimMapper::toSummary);
    }

    /** Picks up an unassigned claim: SUBMITTED → UNDER_REVIEW, assigned to the caller. */
    public ClaimDetailResponse assignToMe(CurrentUser user, String claimNumber) {
        Claim claim = findClaim(claimNumber);
        claim.assignTo(user.id());
        return toDetail(claim);
    }

    public ClaimDetailResponse requestInformation(CurrentUser user, String claimNumber, String question) {
        Claim claim = findClaimAssignedTo(user, claimNumber);
        informationRequestRepository.save(claim.requestInformation(question, user.id()));
        return toDetail(claim);
    }

    public ClaimDetailResponse changeReserve(CurrentUser user, String claimNumber, BigDecimal amount, String reason) {
        Claim claim = findClaimAssignedTo(user, claimNumber);
        BigDecimal previous = claim.getReserveAmount();
        claim.changeReserve(amount);
        // Reserve changes are not stored in a history table yet; the log keeps who/why for now.
        log.info("Reserve of {} changed from {} to {} {} by {}: {}", claimNumber, previous, amount,
                claim.getCurrency(), user.id(), reason);
        return toDetail(claim);
    }

    public ClaimDetailResponse approve(CurrentUser user, String claimNumber, String reason) {
        Claim claim = findClaimAssignedTo(user, claimNumber);
        claim.transitionTo(ClaimStatus.APPROVED, user.id(), reason == null || reason.isBlank() ? "Approved" : reason);
        return toDetail(claim);
    }

    public ClaimDetailResponse reject(CurrentUser user, String claimNumber, String reason) {
        Claim claim = findClaimAssignedTo(user, claimNumber);
        claim.transitionTo(ClaimStatus.REJECTED, user.id(), reason);
        return toDetail(claim);
    }

    public ClaimDetailResponse settle(CurrentUser user, String claimNumber) {
        Claim claim = findClaimAssignedTo(user, claimNumber);
        claim.transitionTo(ClaimStatus.SETTLED, user.id(), "Settled");
        return toDetail(claim);
    }

    private Claim findClaim(String claimNumber) {
        return claimRepository.findByClaimNumber(claimNumber)
                .orElseThrow(() -> new NotFoundException("Claim " + claimNumber + " not found"));
    }

    private Claim findClaimAssignedTo(CurrentUser user, String claimNumber) {
        Claim claim = findClaim(claimNumber);
        String officer = claim.getAssignedOfficerId();
        if (officer == null) {
            throw new ForbiddenException("Claim " + claimNumber + " is not assigned to you. Assign it to yourself first.");
        }
        if (!officer.equals(user.id())) {
            throw new ForbiddenException("Claim " + claimNumber + " is assigned to " + officer + "; only they can act on it");
        }
        return claim;
    }

    private ClaimDetailResponse toDetail(Claim claim) {
        return ClaimMapper.toDetail(claim, informationRequestRepository.findByClaimOrderByRequestedAtAscIdAsc(claim));
    }
}
