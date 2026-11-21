package com.chubb.claims.claim;

import com.chubb.claims.common.ClaimEvent;
import com.chubb.claims.common.ClaimEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Publishes claim events to Kafka ONLY after the database transaction has committed.
 * <ol>
 *   <li>Services call {@link #publish} inside their transaction. This just raises a Spring application event.</li>
 *   <li>{@link #sendAfterCommit} runs after a successful commit and sends it to Kafka. On rollback nothing is sent.</li>
 * </ol>
 * Known gap: if the commit succeeds but the send fails (Kafka down, process crash), the event is lost and only
 * logged. The transactional outbox pattern is the production fix (see ARCHITECTURE.md).
 */
@Component
public class ClaimEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ClaimEventPublisher.class);

    private final ApplicationEventPublisher applicationEvents;
    private final ClaimRepository claimRepository;
    private final KafkaTemplate<String, ClaimEvent> kafkaTemplate;

    public ClaimEventPublisher(ApplicationEventPublisher applicationEvents, ClaimRepository claimRepository,
                               KafkaTemplate<String, ClaimEvent> kafkaTemplate) {
        this.applicationEvents = applicationEvents;
        this.claimRepository = claimRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    /** Call inside the service transaction, after the domain change. */
    public void publish(Claim claim, ClaimEventType type) {
        // Flush so the optimistic-lock version is incremented now: the event carries the version being committed.
        claimRepository.flush();
        applicationEvents.publishEvent(toEvent(claim, type));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendAfterCommit(ClaimEvent event) {
        try {
            kafkaTemplate.send(ClaimEvent.TOPIC, event.claimNumber(), event).whenComplete((result, error) -> {
                if (error != null) {
                    logLostEvent(event, error);
                } else {
                    log.debug("Sent {} for {} (version {})", event.eventType(), event.claimNumber(), event.claimVersion());
                }
            });
        } catch (RuntimeException e) {
            logLostEvent(event, e);
        }
    }

    private static void logLostEvent(ClaimEvent event, Throwable error) {
        log.error("EVENT LOST: database change committed but Kafka send failed. eventId={} type={} claim={} version={}. "
                        + "Notifications/reports will miss this change.",
                event.eventId(), event.eventType(), event.claimNumber(), event.claimVersion(), error);
    }

    static ClaimEvent toEvent(Claim claim, ClaimEventType type) {
        return new ClaimEvent(
                UUID.randomUUID(),
                type,
                Instant.now(),
                claim.getClaimNumber(),
                claim.getVersion(),
                claim.getStatus().name(),
                claim.getMarket(),
                claim.getCurrency(),
                claim.getType().name(),
                claim.getReserveAmount(),
                claim.getAssignedOfficerId(),
                claim.getPolicy().getHolder().getId(),
                claim.getCreatedAt());
    }
}
