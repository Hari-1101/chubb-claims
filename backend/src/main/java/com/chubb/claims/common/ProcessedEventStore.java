package com.chubb.claims.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Consumer idempotency. Each consumer calls {@link #markProcessed} at the start of its transaction:
 * <ul>
 *   <li>true  → first time this consumer group sees the event: do the work (same transaction).</li>
 *   <li>false → already processed (duplicate delivery): skip.</li>
 * </ul>
 * Because the processed_event row and the consumer's work commit together, a crash in between leaves neither,
 * and Kafka's redelivery is handled correctly. ON CONFLICT DO NOTHING also makes two concurrent duplicates safe:
 * the second insert waits for the first transaction and then inserts nothing.
 */
@Component
public class ProcessedEventStore {

    private final JdbcTemplate jdbcTemplate;

    public ProcessedEventStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.MANDATORY) // must join the consumer's transaction
    public boolean markProcessed(UUID eventId, String consumerGroup) {
        int inserted = jdbcTemplate.update("""
                INSERT INTO processed_event (event_id, consumer_group, processed_at)
                VALUES (?, ?, now())
                ON CONFLICT (event_id, consumer_group) DO NOTHING
                """, eventId, consumerGroup);
        return inserted == 1;
    }
}
