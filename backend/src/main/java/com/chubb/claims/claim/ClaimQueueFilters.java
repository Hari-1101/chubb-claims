package com.chubb.claims.claim;

import com.chubb.claims.common.Market;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** Builds the WHERE clause for the staff work queue. Each filter is optional (null = don't filter). */
final class ClaimQueueFilters {

    private ClaimQueueFilters() {
    }

    static Specification<Claim> of(ClaimStatus status, Market market, ProductType type,
                                   AssignmentFilter assigned, String currentUserId) {
        List<Specification<Claim>> filters = new ArrayList<>();
        if (status != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("status"), status));
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
