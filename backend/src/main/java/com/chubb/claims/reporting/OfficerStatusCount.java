package com.chubb.claims.reporting;

import java.time.Instant;

/** One aggregate row from the read model: an officer's open claims in one status. */
public record OfficerStatusCount(String officerId, String status, Long claims, Instant oldestSubmittedAt) {
}
