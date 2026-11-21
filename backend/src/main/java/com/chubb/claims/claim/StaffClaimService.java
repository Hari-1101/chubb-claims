package com.chubb.claims.claim;

import com.chubb.claims.common.ClaimEventType;
import com.chubb.claims.common.CurrentUser;
import com.chubb.claims.common.ForbiddenException;
import com.chubb.claims.common.Market;
import com.chubb.claims.common.NotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Claims-staff use cases. Access rule: once a claim is assigned, only the assigned officer can act on it.
 * Whether a status change is allowed is decided by the domain ({@link Claim} / {@link ClaimStatus}), not here.
 * Every change publishes a claim event (sent to Kafka after commit).
 */
@Service
@Transactional
public class StaffClaimService {

    /** Oldest first so nothing waits too long; id breaks ties. */
    private static final Sort QUEUE_ORDER = Sort.by("createdAt").ascending().and(Sort.by("id").ascending());

    private final ClaimRepository claimRepository;
    private final InformationRequestRepository informationRequestRepository;
    private final ClaimEventPublisher eventPublisher;

    public StaffClaimService(ClaimRepository claimRepository, InformationRequestRepository informationRequestRepository,
                             ClaimEventPublisher eventPublisher) {
        this.claimRepository = claimRepository;
        this.informationRequestRepository = informationRequestRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public PageResponse<ClaimSummaryResponse> queue(CurrentUser user, ClaimStatus status, Market market,
                                                    ProductType type, AssignmentFilter assigned, int page, int size) {
        var filters = ClaimQueueFilters.of(status, market, type, assigned, user.id());
        return PageResponse.of(claimRepository.findAll(filters, PageRequest.of(page, size, QUEUE_ORDER)),
                claim -> ClaimMapper.toSummary(claim, true));
    }

    /** Picks up an unassigned claim: SUBMITTED → UNDER_REVIEW, assigned to the caller. */
    public ClaimDetailResponse assignToMe(CurrentUser user, String claimNumber) {
        Claim claim = findClaim(claimNumber);
        claim.assignTo(user.id());
        return changed(claim, ClaimEventType.ClaimAssigned);
    }

    public ClaimDetailResponse requestInformation(CurrentUser user, String claimNumber, String question) {
        Claim claim = findClaimAssignedTo(user, claimNumber);
        informationRequestRepository.save(claim.requestInformation(question, user.id()));
        return changed(claim, ClaimEventType.InformationRequested);
    }

    public ClaimDetailResponse changeReserve(CurrentUser user, String claimNumber, BigDecimal amount, String reason) {
        Claim claim = findClaimAssignedTo(user, claimNumber);
        claim.changeReserve(amount, user.id(), reason);
        return changed(claim, ClaimEventType.ReserveChanged);
    }

    public ClaimDetailResponse approve(CurrentUser user, String claimNumber, String reason) {
        Claim claim = findClaimAssignedTo(user, claimNumber);
        claim.transitionTo(ClaimStatus.APPROVED, user.id(), reason == null || reason.isBlank() ? "Approved" : reason);
        return changed(claim, ClaimEventType.ClaimApproved);
    }

    public ClaimDetailResponse reject(CurrentUser user, String claimNumber, String reason) {
        Claim claim = findClaimAssignedTo(user, claimNumber);
        claim.transitionTo(ClaimStatus.REJECTED, user.id(), reason);
        return changed(claim, ClaimEventType.ClaimRejected);
    }

    public ClaimDetailResponse settle(CurrentUser user, String claimNumber) {
        Claim claim = findClaimAssignedTo(user, claimNumber);
        claim.transitionTo(ClaimStatus.SETTLED, user.id(), "Settled");
        return changed(claim, ClaimEventType.ClaimSettled);
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

    /** Publishes the event for a successful change and returns the staff view of the claim. */
    private ClaimDetailResponse changed(Claim claim, ClaimEventType eventType) {
        eventPublisher.publish(claim, eventType);
        return ClaimMapper.toDetail(claim, informationRequestRepository.findByClaimOrderByRequestedAtAscIdAsc(claim), true);
    }
}
