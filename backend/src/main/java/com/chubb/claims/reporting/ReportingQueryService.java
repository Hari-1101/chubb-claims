package com.chubb.claims.reporting;

import com.chubb.claims.common.AppUser;
import com.chubb.claims.common.AppUserRepository;
import com.chubb.claims.common.UserRole;
import com.chubb.claims.reporting.WorkloadReport.OfficerWorkload;
import com.chubb.claims.reporting.WorkloadReport.TeamPerformance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Answers report queries from the reporting read model only (never from the claim tables). */
@Service
@Transactional(readOnly = true)
public class ReportingQueryService {

    static final int PERFORMANCE_PERIOD_DAYS = 7;

    private final ReportingClaimRepository repository;
    private final AppUserRepository appUserRepository;

    public ReportingQueryService(ReportingClaimRepository repository, AppUserRepository appUserRepository) {
        this.repository = repository;
        this.appUserRepository = appUserRepository;
    }

    public ExposureReport exposure() {
        return ExposureReport.from(repository.openExposure(ReportingClaim.CLOSED_STATUSES),
                repository.lastUpdated().orElse(null));
    }

    public WorkloadReport workload(String team) {
        Instant now = Instant.now();
        String teamFilter = team == null || team.isBlank() ? null : team.trim();
        List<AppUser> officers = appUserRepository.findByRoleOrderById(UserRole.STAFF).stream()
                .filter(u -> teamFilter == null || u.getTeam().equalsIgnoreCase(teamFilter))
                .toList();
        List<String> officerIds = officers.stream().map(AppUser::getId).toList();

        Map<String, List<OfficerStatusCount>> openByOfficer = repository
                .openClaimsByOfficerAndStatus(ReportingClaim.CLOSED_STATUSES).stream()
                .collect(Collectors.groupingBy(OfficerStatusCount::officerId));

        List<OfficerWorkload> workloads = officers.stream()
                .map(officer -> officerWorkload(officer, openByOfficer.getOrDefault(officer.getId(), List.of()), now))
                .toList();

        List<ReportingClaim> decided = officerIds.isEmpty() ? List.of()
                : repository.findByDecidedAtGreaterThanEqualAndAssignedOfficerIdIn(
                        now.minus(Duration.ofDays(PERFORMANCE_PERIOD_DAYS)), officerIds);

        return new WorkloadReport(
                repository.lastUpdated().orElse(null),
                teamFilter,
                workloads,
                repository.countOpenUnassigned(ReportingClaim.CLOSED_STATUSES),
                new TeamPerformance(PERFORMANCE_PERIOD_DAYS, decided.size(), averageHoursToDecision(decided)));
    }

    private static OfficerWorkload officerWorkload(AppUser officer, List<OfficerStatusCount> counts, Instant now) {
        Map<String, Long> byStatus = new TreeMap<>();
        counts.forEach(c -> byStatus.put(c.status(), c.claims()));
        Instant oldest = counts.stream().map(OfficerStatusCount::oldestSubmittedAt)
                .filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
        return new OfficerWorkload(officer.getId(), officer.getName(), officer.getTeam(),
                byStatus.values().stream().mapToLong(Long::longValue).sum(),
                byStatus,
                oldest,
                oldest == null ? null : Duration.between(oldest, now).toDays());
    }

    private static Double averageHoursToDecision(List<ReportingClaim> decided) {
        if (decided.isEmpty()) {
            return null;
        }
        double averageSeconds = decided.stream()
                .mapToLong(c -> Duration.between(c.getSubmittedAt(), c.getDecidedAt()).toSeconds())
                .average().orElse(0);
        return Math.round(averageSeconds / 3600 * 10) / 10.0; // hours, one decimal
    }
}
