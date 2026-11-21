package com.chubb.claims.reporting;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReportingClaimRepository extends JpaRepository<ReportingClaim, String> {

    /** Open claims grouped by market, currency AND type: amounts are only ever summed within one currency. */
    @Query("""
            select new com.chubb.claims.reporting.ExposureRow(r.market, r.currency, r.type, count(r), sum(r.reserveAmount))
            from ReportingClaim r
            where r.status not in :closedStatuses
            group by r.market, r.currency, r.type
            """)
    List<ExposureRow> openExposure(Collection<String> closedStatuses);

    @Query("""
            select new com.chubb.claims.reporting.OfficerStatusCount(r.assignedOfficerId, r.status, count(r), min(r.submittedAt))
            from ReportingClaim r
            where r.status not in :closedStatuses and r.assignedOfficerId is not null
            group by r.assignedOfficerId, r.status
            """)
    List<OfficerStatusCount> openClaimsByOfficerAndStatus(Collection<String> closedStatuses);

    @Query("select count(r) from ReportingClaim r where r.status not in :closedStatuses and r.assignedOfficerId is null")
    long countOpenUnassigned(Collection<String> closedStatuses);

    List<ReportingClaim> findByDecidedAtGreaterThanEqualAndAssignedOfficerIdIn(Instant since, Collection<String> officerIds);

    /** When the read model last changed: how fresh the reports are. */
    @Query("select max(r.updatedAt) from ReportingClaim r")
    Optional<Instant> lastUpdated();
}
