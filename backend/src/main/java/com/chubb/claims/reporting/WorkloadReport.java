package com.chubb.claims.reporting;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Team workload: open claims per officer, unassigned open claims, and recent team performance.
 *
 * @param lastUpdated          when the reporting read model last changed
 * @param team                 the team filter used, or null for all staff
 * @param unassignedOpenClaims open claims nobody has picked up yet (not team-specific: unassigned claims have no team)
 */
public record WorkloadReport(Instant lastUpdated, String team, List<OfficerWorkload> officers, long unassignedOpenClaims,
                             TeamPerformance teamPerformance) {

    /**
     * @param oldestOpenClaimAgeDays whole days since the officer's oldest open claim was submitted; null if none
     */
    public record OfficerWorkload(String officerId, String name, String team, long openClaims,
                                  Map<String, Long> openClaimsByStatus, Instant oldestOpenClaimSubmittedAt,
                                  Long oldestOpenClaimAgeDays) {
    }

    /**
     * @param claimsDecided          claims approved or rejected by these officers in the last periodDays days
     * @param averageHoursToDecision average time from submission to decision for those claims; null if none
     */
    public record TeamPerformance(int periodDays, long claimsDecided, Double averageHoursToDecision) {
    }
}
