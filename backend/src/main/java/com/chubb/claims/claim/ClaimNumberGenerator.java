package com.chubb.claims.claim;

import com.chubb.claims.common.Market;
import org.springframework.stereotype.Component;

/**
 * Generates claim numbers in the format CLM-&lt;MARKET&gt;-&lt;YEAR&gt;-&lt;6-digit sequence&gt;, e.g. CLM-SG-2026-000001.
 * The sequence comes from the Postgres sequence claim_number_seq, so numbers are unique even with concurrent
 * submissions. The year is the current year in the claim's market.
 */
@Component
public class ClaimNumberGenerator {

    private final ClaimRepository claimRepository;

    public ClaimNumberGenerator(ClaimRepository claimRepository) {
        this.claimRepository = claimRepository;
    }

    public String next(Market market) {
        long sequence = claimRepository.nextClaimNumberSequence();
        return format(market, market.today().getYear(), sequence);
    }

    static String format(Market market, int year, long sequence) {
        return "CLM-%s-%d-%06d".formatted(market.name(), year, sequence);
    }
}
