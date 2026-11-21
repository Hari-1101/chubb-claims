package com.chubb.claims.claim;

import com.chubb.claims.common.CurrentUser;
import com.chubb.claims.common.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

import static com.chubb.claims.claim.TestData.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Access rules for staff use cases: only the assigned officer can act (Mockito, no database). */
@ExtendWith(MockitoExtension.class)
class StaffClaimServiceTest {

    @Mock ClaimRepository claimRepository;
    @Mock InformationRequestRepository informationRequestRepository;
    @InjectMocks StaffClaimService service;

    /** Every action that requires the caller to be the assigned officer. */
    private Map<String, BiConsumer<CurrentUser, String>> officerActions() {
        return Map.of(
                "requestInformation", (u, n) -> service.requestInformation(u, n, "Photos?"),
                "changeReserve", (u, n) -> service.changeReserve(u, n, new BigDecimal("100.00"), "re-estimate"),
                "approve", (u, n) -> service.approve(u, n, "ok"),
                "reject", (u, n) -> service.reject(u, n, "not covered"),
                "settle", (u, n) -> service.settle(u, n));
    }

    private Claim claimAssignedTo(CurrentUser officer) {
        Claim claim = claimOf(ALICE, "CLM-A");
        claim.assignTo(officer.id());
        when(claimRepository.findByClaimNumber("CLM-A")).thenReturn(Optional.of(claim));
        return claim;
    }

    @Test
    void assignSetsTheCallerAsOfficer() {
        Claim claim = claimOf(ALICE, "CLM-A");
        when(claimRepository.findByClaimNumber("CLM-A")).thenReturn(Optional.of(claim));

        ClaimDetailResponse result = service.assignToMe(OFFICER_1, "CLM-A");

        assertEquals("S001", result.assignedOfficerId());
        assertEquals(ClaimStatus.UNDER_REVIEW, result.status());
    }

    @Test
    void otherOfficerCannotActOnAnAssignedClaim() {
        officerActions().forEach((name, action) -> {
            Claim claim = claimAssignedTo(OFFICER_1);
            BigDecimal reserveBefore = claim.getReserveAmount();

            ForbiddenException ex = assertThrows(ForbiddenException.class, () -> action.accept(OFFICER_2, "CLM-A"), name);

            assertTrue(ex.getMessage().contains("assigned to S001"), name + ": " + ex.getMessage());
            assertEquals(ClaimStatus.UNDER_REVIEW, claim.getStatus(), name + ": status unchanged");
            assertEquals(reserveBefore, claim.getReserveAmount(), name + ": reserve unchanged");
        });
        verify(informationRequestRepository, never()).save(any());
    }

    @Test
    void nobodyCanActOnAClaimBeforeItIsAssigned() {
        officerActions().forEach((name, action) -> {
            Claim claim = claimOf(ALICE, "CLM-A");
            when(claimRepository.findByClaimNumber("CLM-A")).thenReturn(Optional.of(claim));

            assertThrows(ForbiddenException.class, () -> action.accept(OFFICER_1, "CLM-A"), name);
            assertEquals(ClaimStatus.SUBMITTED, claim.getStatus(), name + ": status unchanged");
        });
    }

    @Test
    void assignedOfficerCanApproveAndIsRecordedInHistory() {
        Claim claim = claimAssignedTo(OFFICER_1);

        ClaimDetailResponse result = service.approve(OFFICER_1, "CLM-A", "Covered under section 2");

        assertEquals(ClaimStatus.APPROVED, result.status());
        var last = result.statusHistory().get(result.statusHistory().size() - 1);
        assertEquals("S001", last.changedBy());
        assertEquals("Covered under section 2", last.reason());
        assertEquals(ClaimStatus.APPROVED, claim.getStatus());
    }

    @Test
    void assignedOfficerCanRequestInformationAndTheRequestIsSaved() {
        claimAssignedTo(OFFICER_1);

        ClaimDetailResponse result = service.requestInformation(OFFICER_1, "CLM-A", "Police report please");

        assertEquals(ClaimStatus.INFO_REQUESTED, result.status());
        verify(informationRequestRepository).save(any(InformationRequest.class));
    }
}
