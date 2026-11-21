package com.chubb.claims.reporting;

import com.chubb.claims.common.RequireRole;
import com.chubb.claims.common.UserRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff/reports")
@RequireRole(UserRole.STAFF)
@Tag(name = "Reports", description = "Liability exposure and team workload from the reporting read model. "
        + "It is updated from Kafka events, so it can lag a moment behind: check lastUpdated.")
public class ReportingController {

    private final ReportingQueryService queryService;

    public ReportingController(ReportingQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/exposure")
    @Operation(summary = "Liability exposure of open claims by market and currency (never summed across currencies)")
    public ExposureReport exposure() {
        return queryService.exposure();
    }

    @GetMapping("/workload")
    @Operation(summary = "Open claims per officer, unassigned claims and team performance (last 7 days)",
            description = "team is optional (Motor or Property); without it all staff are included.")
    public WorkloadReport workload(@RequestParam(required = false) String team) {
        return queryService.workload(team);
    }
}
