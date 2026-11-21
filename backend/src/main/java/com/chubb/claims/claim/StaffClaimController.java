package com.chubb.claims.claim;

import com.chubb.claims.common.CurrentUser;
import com.chubb.claims.common.Market;
import com.chubb.claims.common.RequireRole;
import com.chubb.claims.common.UserRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff/claims")
@RequireRole(UserRole.STAFF)
@Tag(name = "Staff")
public class StaffClaimController {

    private final StaffClaimService staffClaimService;

    public StaffClaimController(StaffClaimService staffClaimService) {
        this.staffClaimService = staffClaimService;
    }

    @GetMapping
    @Operation(summary = "Work queue, oldest first",
            description = "All filters are optional. Without a status filter only open claims are shown.")
    public PageResponse<ClaimSummaryResponse> queue(
            CurrentUser user,
            @RequestParam(required = false) ClaimStatus status,
            @RequestParam(required = false) Market market,
            @RequestParam(required = false) ProductType type,
            @RequestParam(defaultValue = "any") AssignmentFilter assigned,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return staffClaimService.queue(user, status, market, type, assigned, page, size);
    }

    @PostMapping("/{claimNumber}/assign")
    @Operation(summary = "Assign the claim to myself (SUBMITTED → UNDER_REVIEW)")
    public ClaimDetailResponse assign(CurrentUser user, @PathVariable String claimNumber) {
        return staffClaimService.assignToMe(user, claimNumber);
    }

    @PostMapping("/{claimNumber}/information-requests")
    @Operation(summary = "Ask the claimant for information (UNDER_REVIEW → INFO_REQUESTED)")
    public ClaimDetailResponse requestInformation(CurrentUser user, @PathVariable String claimNumber,
                                                  @Valid @RequestBody RequestInformationRequest request) {
        return staffClaimService.requestInformation(user, claimNumber, request.question());
    }

    @PutMapping("/{claimNumber}/reserve")
    @Operation(summary = "Change the reserve (must not exceed the policy coverage limit)")
    public ClaimDetailResponse changeReserve(CurrentUser user, @PathVariable String claimNumber,
                                             @Valid @RequestBody ChangeReserveRequest request) {
        return staffClaimService.changeReserve(user, claimNumber, request.amount(), request.reason());
    }

    @PostMapping("/{claimNumber}/approve")
    @Operation(summary = "Approve the claim (UNDER_REVIEW → APPROVED). Body and reason are optional.")
    public ClaimDetailResponse approve(CurrentUser user, @PathVariable String claimNumber,
                                       @Valid @RequestBody(required = false) DecisionRequest request) {
        return staffClaimService.approve(user, claimNumber, request == null ? null : request.reason());
    }

    @PostMapping("/{claimNumber}/reject")
    @Operation(summary = "Reject the claim (UNDER_REVIEW → REJECTED). Reason required.")
    public ClaimDetailResponse reject(CurrentUser user, @PathVariable String claimNumber,
                                      @Valid @RequestBody RejectClaimRequest request) {
        return staffClaimService.reject(user, claimNumber, request.reason());
    }

    @PostMapping("/{claimNumber}/settle")
    @Operation(summary = "Settle an approved claim (APPROVED → SETTLED)")
    public ClaimDetailResponse settle(CurrentUser user, @PathVariable String claimNumber) {
        return staffClaimService.settle(user, claimNumber);
    }
}
