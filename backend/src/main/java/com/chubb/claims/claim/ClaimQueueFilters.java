package com.chubb.claims.claim;

import com.chubb.claims.common.Market;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Builds the WHERE clause for the staff work queue. Each filter is optional.
 * Without a status filter the queue shows only open claims (SETTLED and REJECTED are hidden).
 */
final class ClaimQueueFilters {

    private static final List<ClaimStatus> OPEN_STATUSES =
            Arrays.stream(ClaimStatus.values()).filter(s -> !s.isFinal()).toList();

    private ClaimQueueFilters() {
    }

    static Specification<Claim> of(ClaimStatus status, Market market, ProductType type,
                                   AssignmentFilter assigned, String currentUserId) {
        List<Specification<Claim>> filters = new ArrayList<>();
        if (status != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("status"), status));
        } else {
            filters.add((root, query, cb) -> root.get("status").in(OPEN_STATUSES));
        }
        if (market != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("market"), market));
        }
        if (type != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("type"), type));
        }
        if (assigned == AssignmentFilter.me) {
            filters.add((root, query, cb) -> cb.equal(root.get("assignedOfficerId"), currentUserId));
        } else if (assigned == AssignmentFilter.unassigned) {
            filters.add((root, query, cb) -> cb.isNull(root.get("assignedOfficerId")));
        }
        return Specification.allOf(filters);
    }
}
