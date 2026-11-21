package com.chubb.claims.claim;

import com.chubb.claims.common.AppUser;
import com.chubb.claims.common.BusinessRuleException;
import com.chubb.claims.common.InvalidStatusTransitionException;
import com.chubb.claims.common.Market;
import com.chubb.claims.common.UserRole;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.chubb.claims.claim.ClaimStatus.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plain unit tests for the Claim aggregate: no Spring, no database. */
class ClaimTest {

    static final String CLAIMANT_ID = "C001";
    static final String OFFICER_ID = "S001";

    static final AppUser CLAIMANT = new AppUser(CLAIMANT_ID, "Test Claimant", "c@example.com", UserRole.CLAIMANT, null);

    static Policy policy(Market market, ProductType type, String coverageLimit) {
        return new Policy("POL-TEST", CLAIMANT, market, type, new BigDecimal(coverageLimit));
    }

    static Claim newClaim(Policy policy, String estimate) {
        return Claim.submit("CLM-TEST", policy, policy.getMarket().today(), "Rear-ended at traffic light",
                new BigDecimal(estimate), CLAIMANT_ID);
    }

    static Claim newClaim() {
        return newClaim(policy(Market.SG, ProductType.MOTOR, "150000.00"), "12000.00");
    }

    /** Shortest valid path from SUBMITTED to each status. */
    static final Map<ClaimStatus, List<ClaimStatus>> PATH = Map.of(
            SUBMITTED, List.of(),
            UNDER_REVIEW, List.of(UNDER_REVIEW),
            INFO_REQUESTED, List.of(UNDER_REVIEW, INFO_REQUESTED),
            APPROVED, List.of(UNDER_REVIEW, APPROVED),
            SETTLED, List.of(UNDER_REVIEW, APPROVED, SETTLED),
            REJECTED, List.of(UNDER_REVIEW, REJECTED));

    static Claim claimIn(ClaimStatus status) {
        Claim claim = newClaim();
        PATH.get(status).forEach(next -> claim.transitionTo(next, OFFICER_ID, "setup"));
        assertEquals(status, claim.getStatus());
        return claim;
    }

    @Nested
    class Creation {

        @Test
        void newClaimIsSubmittedWithValuesCopiedFromPolicy() {
            Policy policy = policy(Market.AU, ProductType.PROPERTY, "1200000.00");
            Claim claim = newClaim(policy, "45000.50");

            assertEquals(SUBMITTED, claim.getStatus());
            assertEquals(ProductType.PROPERTY, claim.getType());
            assertEquals(Market.AU, claim.getMarket());
            assertEquals("AUD", claim.getCurrency());
            assertEquals(new BigDecimal("45000.50"), claim.getEstimatedAmount());
            assertEquals(new BigDecimal("45000.50"), claim.getReserveAmount(), "reserve starts equal to estimate when within cover");
            assertNull(claim.getAssignedOfficerId());
            assertNotNull(claim.getCreatedAt());
            assertEquals(claim.getCreatedAt(), claim.getUpdatedAt());
            assertTrue(claim.isOpen());
        }

        @Test
        void creationRecordsInitialHistoryEntry() {
            Claim claim = newClaim();

            assertEquals(1, claim.getStatusHistory().size());
            ClaimStatusHistory entry = claim.getStatusHistory().get(0);
            assertNull(entry.getFromStatus());
            assertEquals(SUBMITTED, entry.getToStatus());
            assertEquals(CLAIMANT_ID, entry.getChangedBy());
        }

        @Test
        void incidentDateTodayIsAccepted() {
            Policy policy = policy(Market.SG, ProductType.MOTOR, "150000.00");
            Claim claim = Claim.submit("CLM-TEST", policy, Market.SG.today(), "desc", BigDecimal.TEN, CLAIMANT_ID);
            assertEquals(Market.SG.today(), claim.getIncidentDate());
        }

        @Test
        void incidentDateInTheFutureIsRejected() {
            Policy policy = policy(Market.SG, ProductType.MOTOR, "150000.00");
            LocalDate tomorrow = Market.SG.today().plusDays(1);

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> Claim.submit("CLM-TEST", policy, tomorrow, "desc", BigDecimal.TEN, CLAIMANT_ID));
            assertTrue(ex.getMessage().contains("cannot be in the future"));
        }

        @Test
        void blankDescriptionIsRejected() {
            Policy policy = policy(Market.SG, ProductType.MOTOR, "150000.00");
            assertThrows(BusinessRuleException.class,
                    () -> Claim.submit("CLM-TEST", policy, Market.SG.today(), "  ", BigDecimal.TEN, CLAIMANT_ID));
        }

        @Test
        void negativeEstimateIsRejected() {
            Policy policy = policy(Market.SG, ProductType.MOTOR, "150000.00");
            assertThrows(BusinessRuleException.class, () -> newClaim(policy, "-1.00"));
        }

        @Test
        void estimateEqualToCoverageLimitIsAccepted() {
            Claim claim = newClaim(policy(Market.SG, ProductType.MOTOR, "150000.00"), "150000.00");
            assertEquals(new BigDecimal("150000.00"), claim.getReserveAmount());
        }

        @Test
        void estimateAboveCoverageLimitIsKeptButReserveIsCappedAtTheLimit() {
            Claim claim = newClaim(policy(Market.SG, ProductType.MOTOR, "150000.00"), "200000.00");
            assertEquals(new BigDecimal("200000.00"), claim.getEstimatedAmount(), "estimate kept as entered");
            assertEquals(new BigDecimal("150000.00"), claim.getReserveAmount(), "reserve capped at coverage limit");
        }
    }

    @Nested
    class Reserve {

        @Test
        void reserveCanBeChangedWithinLimits() {
            Claim claim = newClaim();
            claim.changeReserve(new BigDecimal("0.00"));
            assertEquals(new BigDecimal("0.00"), claim.getReserveAmount());
            claim.changeReserve(new BigDecimal("150000.00"));
            assertEquals(new BigDecimal("150000.00"), claim.getReserveAmount());
        }

        @Test
        void negativeReserveIsRejected() {
            Claim claim = newClaim();
            assertThrows(BusinessRuleException.class, () -> claim.changeReserve(new BigDecimal("-0.01")));
            assertEquals(new BigDecimal("12000.00"), claim.getReserveAmount(), "reserve unchanged");
        }

        @Test
        void reserveAboveCoverageLimitIsRejected() {
            Claim claim = newClaim();
            assertThrows(BusinessRuleException.class, () -> claim.changeReserve(new BigDecimal("150000.01")));
            assertEquals(new BigDecimal("12000.00"), claim.getReserveAmount(), "reserve unchanged");
        }
    }

    @Nested
    class Transitions {

        @Test
        void everyAllowedTransitionChangesStatusAndWritesHistory() {
            for (ClaimStatus from : ClaimStatus.values()) {
                for (ClaimStatus to : from.allowedNextStatuses()) {
                    Claim claim = claimIn(from);
                    int historyBefore = claim.getStatusHistory().size();

                    claim.transitionTo(to, OFFICER_ID, "because " + to);

                    assertEquals(to, claim.getStatus(), from + " -> " + to);
                    assertEquals(historyBefore + 1, claim.getStatusHistory().size());
                    ClaimStatusHistory last = claim.getStatusHistory().get(historyBefore);
                    assertEquals(from, last.getFromStatus());
                    assertEquals(to, last.getToStatus());
                    assertEquals(OFFICER_ID, last.getChangedBy());
                    assertEquals("because " + to, last.getReason());
                    assertNotNull(last.getChangedAt());
                }
            }
        }

        @Test
        void everyDisallowedTransitionThrowsAndChangesNothing() {
            for (ClaimStatus from : ClaimStatus.values()) {
                for (ClaimStatus to : ClaimStatus.values()) {
                    if (from.canTransitionTo(to)) {
                        continue;
                    }
                    Claim claim = claimIn(from);
                    int historyBefore = claim.getStatusHistory().size();

                    InvalidStatusTransitionException ex = assertThrows(InvalidStatusTransitionException.class,
                            () -> claim.transitionTo(to, OFFICER_ID, "nope"), from + " -> " + to);

                    assertEquals(from.name(), ex.getFromStatus());
                    assertEquals(to.name(), ex.getToStatus());
                    assertEquals(from, claim.getStatus(), "status unchanged");
                    assertEquals(historyBefore, claim.getStatusHistory().size(), "no history written");
                }
            }
        }

        @Test
        void finalStatusesCannotMoveAnywhere() {
            for (ClaimStatus finalStatus : List.of(SETTLED, REJECTED)) {
                Claim claim = claimIn(finalStatus);
                assertFalse(claim.isOpen());
                for (ClaimStatus to : ClaimStatus.values()) {
                    InvalidStatusTransitionException ex = assertThrows(InvalidStatusTransitionException.class,
                            () -> claim.transitionTo(to, OFFICER_ID, "nope"));
                    assertTrue(ex.getMessage().contains("is final"), ex.getMessage());
                }
            }
        }

        @Test
        void errorMessageExplainsWhatIsAllowed() {
            Claim claim = newClaim();
            InvalidStatusTransitionException ex = assertThrows(InvalidStatusTransitionException.class,
                    () -> claim.transitionTo(APPROVED, OFFICER_ID, null));
            assertEquals("Cannot move claim CLM-TEST from SUBMITTED to APPROVED. Allowed next statuses: [UNDER_REVIEW].",
                    ex.getMessage());
        }

        @Test
        void infoRequestRoundTripReturnsToUnderReview() {
            Claim claim = claimIn(UNDER_REVIEW);
            claim.transitionTo(INFO_REQUESTED, OFFICER_ID, "Need police report");
            claim.transitionTo(UNDER_REVIEW, CLAIMANT_ID, "Report provided");
            claim.transitionTo(APPROVED, OFFICER_ID, "Covered");
            claim.transitionTo(SETTLED, OFFICER_ID, "Paid");

            List<ClaimStatus> toStatuses = claim.getStatusHistory().stream().map(ClaimStatusHistory::getToStatus).toList();
            assertEquals(List.of(SUBMITTED, UNDER_REVIEW, INFO_REQUESTED, UNDER_REVIEW, APPROVED, SETTLED), toStatuses);
        }
    }

    @Nested
    class Policies {

        @Test
        void policyCurrencyFollowsMarket() {
            for (Market market : Market.values()) {
                assertEquals(market.currency(), policy(market, ProductType.MOTOR, "1000").getCurrency());
            }
        }

        @Test
        void policyHolderMustBeAClaimant() {
            AppUser staff = new AppUser("S001", "Staff", "s@example.com", UserRole.STAFF, "Motor");
            assertThrows(BusinessRuleException.class,
                    () -> new Policy("POL-X", staff, Market.SG, ProductType.MOTOR, new BigDecimal("1000")));
        }
    }

    @Nested
    class ClaimNumbers {

        @Test
        void claimNumberHasMarketYearAndSixDigitSequence() {
            assertEquals("CLM-SG-2026-000001", ClaimNumberGenerator.format(Market.SG, 2026, 1));
            assertEquals("CLM-ID-2027-123456", ClaimNumberGenerator.format(Market.ID, 2027, 123456));
        }
    }
}
