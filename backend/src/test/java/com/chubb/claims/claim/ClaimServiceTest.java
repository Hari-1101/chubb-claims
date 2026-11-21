package com.chubb.claims.claim;

import com.chubb.claims.common.ForbiddenException;
import com.chubb.claims.common.Market;
import com.chubb.claims.common.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static com.chubb.claims.claim.TestData.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Access rules for claimant use cases (Mockito, no database). */
@ExtendWith(MockitoExtension.class)
class ClaimServiceTest {

    @Mock ClaimRepository claimRepository;
    @Mock PolicyRepository policyRepository;
    @Mock InformationRequestRepository informationRequestRepository;
    @Mock ClaimNumberGenerator claimNumberGenerator;
    @InjectMocks ClaimService service;

    private static SubmitClaimRequest submitRequest(String policyNumber) {
        return new SubmitClaimRequest(policyNumber, Market.SG.today(), "Rear-ended", new BigDecimal("4200.00"));
    }

    @Test
    void claimantCanSubmitOnOwnPolicy() {
        when(policyRepository.findByPolicyNumber("POL-A")).thenReturn(Optional.of(policyOf(ALICE, "POL-A")));
        when(claimNumberGenerator.next(Market.SG)).thenReturn("CLM-SG-2026-000001");

        ClaimDetailResponse result = service.submit(ALICE_USER, submitRequest("POL-A"));

        assertEquals("CLM-SG-2026-000001", result.claimNumber());
        assertEquals(ClaimStatus.SUBMITTED, result.status());
        verify(claimRepository).save(any(Claim.class));
    }

    @Test
    void claimantCannotSubmitOnSomeoneElsesPolicy() {
        when(policyRepository.findByPolicyNumber("POL-B")).thenReturn(Optional.of(policyOf(BOB, "POL-B")));

        assertThrows(ForbiddenException.class, () -> service.submit(ALICE_USER, submitRequest("POL-B")));

        verify(claimRepository, never()).save(any());
        verifyNoInteractions(claimNumberGenerator);
    }

    @Test
    void submittingOnUnknownPolicyIsNotFound() {
        when(policyRepository.findByPolicyNumber("POL-X")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.submit(ALICE_USER, submitRequest("POL-X")));
        verify(claimRepository, never()).save(any());
    }

    @Test
    void claimantCanSeeOwnClaim() {
        when(claimRepository.findByClaimNumber("CLM-A")).thenReturn(Optional.of(claimOf(ALICE, "CLM-A")));

        assertEquals("CLM-A", service.getClaim(ALICE_USER, "CLM-A").claimNumber());
    }

    @Test
    void claimantCannotSeeSomeoneElsesClaimAndGets404() {
        when(claimRepository.findByClaimNumber("CLM-B")).thenReturn(Optional.of(claimOf(BOB, "CLM-B")));

        NotFoundException ex = assertThrows(NotFoundException.class, () -> service.getClaim(ALICE_USER, "CLM-B"));
        assertEquals("Claim CLM-B not found", ex.getMessage(), "same message as a claim that doesn't exist");
    }

    @Test
    void staffCanSeeAnyClaim() {
        when(claimRepository.findByClaimNumber("CLM-B")).thenReturn(Optional.of(claimOf(BOB, "CLM-B")));

        assertEquals("CLM-B", service.getClaim(OFFICER_1, "CLM-B").claimNumber());
    }

    @Test
    void claimantCannotRespondOnSomeoneElsesClaim() {
        Claim bobsClaim = claimOf(BOB, "CLM-B");
        bobsClaim.assignTo(OFFICER_1.id());
        InformationRequest request = bobsClaim.requestInformation("Photos?", OFFICER_1.id());
        when(claimRepository.findByClaimNumber("CLM-B")).thenReturn(Optional.of(bobsClaim));

        assertThrows(NotFoundException.class,
                () -> service.respondToInformationRequest(ALICE_USER, "CLM-B", 1L, "Hacked"));

        assertEquals(ClaimStatus.INFO_REQUESTED, bobsClaim.getStatus());
        assertEquals(true, request.isOpen());
        verifyNoInteractions(informationRequestRepository);
    }
}
