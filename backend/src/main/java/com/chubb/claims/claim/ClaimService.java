package com.chubb.claims.claim;

import com.chubb.claims.common.ClaimEventType;
import com.chubb.claims.common.CurrentUser;
import com.chubb.claims.common.ForbiddenException;
import com.chubb.claims.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Claimant use cases, plus the claim detail view shared with staff.
 * Access rules: a claimant only works with their own policies and claims.
 * Every change publishes a claim event (sent to Kafka after commit).
 */
@Service
@Transactional
public class ClaimService {

    private final ClaimRepository claimRepository;
    private final PolicyRepository policyRepository;
    private final InformationRequestRepository informationRequestRepository;
    private final ClaimNumberGenerator claimNumberGenerator;
    private final ClaimEventPublisher eventPublisher;

    public ClaimService(ClaimRepository claimRepository, PolicyRepository policyRepository,
                        InformationRequestRepository informationRequestRepository,
                        ClaimNumberGenerator claimNumberGenerator, ClaimEventPublisher eventPublisher) {
        this.claimRepository = claimRepository;
        this.policyRepository = policyRepository;
        this.informationRequestRepository = informationRequestRepository;
        this.claimNumberGenerator = claimNumberGenerator;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<PolicyResponse> myPolicies(CurrentUser user) {
        return policyRepository.findByHolderIdOrderByPolicyNumber(user.id()).stream()
                .map(ClaimMapper::toResponse)
                .toList();
    }

    /** Submits a claim on one of the caller's own policies (403 if the policy belongs to someone else). */
    public ClaimDetailResponse submit(CurrentUser user, SubmitClaimRequest request) {
        Policy policy = policyRepository.findByPolicyNumber(request.policyNumber())
                .orElseThrow(() -> new NotFoundException("Policy " + request.policyNumber() + " not found"));
        if (!policy.getHolder().getId().equals(user.id())) {
            throw new ForbiddenException("Policy " + request.policyNumber() + " does not belong to you");
        }
        Claim claim = Claim.submit(claimNumberGenerator.next(policy.getMarket()), policy, request.incidentDate(),
                request.description(), request.estimatedAmount(), user.id());
        claimRepository.save(claim);
        eventPublisher.publish(claim, ClaimEventType.ClaimSubmitted);
        return ClaimMapper.toDetail(claim, List.of(), user.isStaff());
    }

    @Transactional(readOnly = true)
    public List<ClaimSummaryResponse> myClaims(CurrentUser user) {
        return claimRepository.findByPolicyHolderIdOrderByCreatedAtDescIdDesc(user.id()).stream()
                .map(claim -> ClaimMapper.toSummary(claim, user.isStaff()))
                .toList();
    }

    /** Staff see any claim; claimants only their own (others look like 404 so we don't reveal they exist). */
    @Transactional(readOnly = true)
    public ClaimDetailResponse getClaim(CurrentUser user, String claimNumber) {
        return toDetail(user, findVisibleClaim(user, claimNumber));
    }

    /** The claimant answers an open information request; the claim goes back to UNDER_REVIEW. */
    public ClaimDetailResponse respondToInformationRequest(CurrentUser user, String claimNumber, Long requestId,
                                                           String response) {
        Claim claim = findVisibleClaim(user, claimNumber);
        InformationRequest request = informationRequestRepository.findByIdAndClaim(requestId, claim)
                .orElseThrow(() -> new NotFoundException(
                        "Information request " + requestId + " not found on claim " + claimNumber));
        claim.respondToInformationRequest(request, response, user.id());
        eventPublisher.publish(claim, ClaimEventType.InformationResponded);
        return toDetail(user, claim);
    }

    private Claim findVisibleClaim(CurrentUser user, String claimNumber) {
        Claim claim = claimRepository.findByClaimNumber(claimNumber)
                .orElseThrow(() -> claimNotFound(claimNumber));
        if (user.isClaimant() && !claim.getPolicy().getHolder().getId().equals(user.id())) {
            throw claimNotFound(claimNumber);
        }
        return claim;
    }

    /** Reserve amounts are internal: only staff see them. */
    private ClaimDetailResponse toDetail(CurrentUser user, Claim claim) {
        return ClaimMapper.toDetail(claim, informationRequestRepository.findByClaimOrderByRequestedAtAscIdAsc(claim),
                user.isStaff());
    }

    private static NotFoundException claimNotFound(String claimNumber) {
        return new NotFoundException("Claim " + claimNumber + " not found");
    }
}
