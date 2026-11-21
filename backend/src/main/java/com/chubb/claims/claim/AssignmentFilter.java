package com.chubb.claims.claim;

/**
 * Work-queue filter on assignment, used as the {@code assigned} query parameter.
 * Lower-case on purpose so the URL reads naturally: {@code ?assigned=me}.
 */
public enum AssignmentFilter {
    me,
    unassigned,
    any
}
