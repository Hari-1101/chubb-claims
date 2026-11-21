package com.chubb.claims.claim;

import com.chubb.claims.common.CurrentUser;
import com.chubb.claims.common.RequireRole;
import com.chubb.claims.common.UserRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequireRole(UserRole.CLAIMANT)
@Tag(name = "Claimant")
public class ClaimantController {

    private final ClaimService claimService;

    public ClaimantController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @GetMapping("/me/policies")
    @Operation(summary = "My policies")
    public List<PolicyResponse> myPolicies(CurrentUser user) {
        return claimService.myPolicies(user);
    }

    @PostMapping("/claims")
    @Operation(summary = "Submit a claim on one of my policies")
    public ResponseEntity<ClaimDetailResponse> submit(CurrentUser user, @Valid @RequestBody SubmitClaimRequest request) {
        ClaimDetailResponse claim = claimService.submit(user, request);
        return ResponseEntity.created(URI.create("/api/claims/" + claim.claimNumber())).body(claim);
    }

    @GetMapping("/claims/mine")
    @Operation(summary = "My claims, newest first")
    public List<ClaimSummaryResponse> myClaims(CurrentUser user) {
        return claimService.myClaims(user);
    }

    @GetMapping("/claims/{claimNumber}")
    @RequireRole({UserRole.CLAIMANT, UserRole.STAFF})
    @Operation(summary = "Claim detail with status history and information requests",
            description = "Claimants can only see their own claims (404 otherwise); staff can see any claim.",
            tags = {"Claimant", "Staff"})
    public ClaimDetailResponse getClaim(CurrentUser user, @PathVariable String claimNumber) {
        return claimService.getClaim(user, claimNumber);
    }

    @PostMapping("/claims/{claimNumber}/information-requests/{requestId}/response")
    @Operation(summary = "Answer an open information request (INFO_REQUESTED → UNDER_REVIEW)")
    public ClaimDetailResponse respond(CurrentUser user, @PathVariable String claimNumber, @PathVariable Long requestId,
                                       @Valid @RequestBody InformationResponseRequest request) {
        return claimService.respondToInformationRequest(user, claimNumber, requestId, request.response());
    }
}
