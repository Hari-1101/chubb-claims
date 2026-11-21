package com.chubb.claims.claim;

import com.chubb.claims.common.AppUser;
import com.chubb.claims.common.CurrentUser;
import com.chubb.claims.common.Market;
import com.chubb.claims.common.UserRole;

import java.math.BigDecimal;

/** In-memory test objects for service unit tests (no database). */
final class TestData {

    static final AppUser ALICE = new AppUser("C001", "Alice", "alice@example.com", UserRole.CLAIMANT, null);
    static final AppUser BOB = new AppUser("C002", "Bob", "bob@example.com", UserRole.CLAIMANT, null);

    static final CurrentUser ALICE_USER = CurrentUser.from(ALICE);
    static final CurrentUser BOB_USER = CurrentUser.from(BOB);
    static final CurrentUser OFFICER_1 = new CurrentUser("S001", "Officer One", UserRole.STAFF, "Motor");
    static final CurrentUser OFFICER_2 = new CurrentUser("S002", "Officer Two", UserRole.STAFF, "Motor");

    private TestData() {
    }

    static Policy policyOf(AppUser holder, String policyNumber) {
        return new Policy(policyNumber, holder, Market.SG, ProductType.MOTOR, new BigDecimal("150000.00"));
    }

    static Claim claimOf(AppUser holder, String claimNumber) {
        return Claim.submit(claimNumber, policyOf(holder, "POL-" + holder.getId()), Market.SG.today(),
                "Collision", new BigDecimal("5000.00"), holder.getId());
    }
}
