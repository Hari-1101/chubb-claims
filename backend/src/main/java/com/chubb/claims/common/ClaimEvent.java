package com.chubb.claims.common;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A claim domain event as sent to Kafka (topic from property {@code claims.events.topic}, default "claims.events";
 * key = claimNumber, JSON value).
 * It is the contract between the claim module (publisher) and the notification/reporting modules (consumers),
 * which is why it lives in {@code common} and uses plain strings for status and type.
 * <p>
 * Besides the envelope (eventId … claimVersion) it carries a snapshot of the claim after the change, so consumers
 * never have to read the claim tables.
 *
 * @param eventId      unique per event; consumers use it to skip duplicates
 * @param claimVersion the claim's optimistic-lock version after the change; increases with every change, so
 *                     consumers can ignore older or repeated snapshots
 */
public record ClaimEvent(
        UUID eventId,
        ClaimEventType eventType,
        Instant occurredAt,
        String claimNumber,
        long claimVersion,
        String status,
        Market market,
        String currency,
        String type,
        BigDecimal reserveAmount,
        String assignedOfficerId,
        String claimantId,
        Instant submittedAt) {

}
