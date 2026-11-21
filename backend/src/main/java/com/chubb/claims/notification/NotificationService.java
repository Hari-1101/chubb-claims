package com.chubb.claims.notification;

import com.chubb.claims.common.AppUser;
import com.chubb.claims.common.AppUserRepository;
import com.chubb.claims.common.ClaimEvent;
import com.chubb.claims.common.ProcessedEventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Turns claim events into notifications for the claimant. Safe to call twice with the same event:
 * the processed_event check and the notification insert happen in one transaction.
 */
@Service
public class NotificationService {

    public static final String CONSUMER_GROUP = "claims-notification";

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final AppUserRepository appUserRepository;
    private final ProcessedEventStore processedEvents;

    public NotificationService(NotificationRepository notificationRepository, AppUserRepository appUserRepository,
                               ProcessedEventStore processedEvents) {
        this.notificationRepository = notificationRepository;
        this.appUserRepository = appUserRepository;
        this.processedEvents = processedEvents;
    }

    @Transactional
    public void handle(ClaimEvent event) {
        Optional<String> message = messageFor(event);
        if (message.isEmpty()) {
            return; // not an event claimants are told about
        }
        if (!processedEvents.markProcessed(event.eventId(), CONSUMER_GROUP)) {
            log.info("Skipping duplicate event {} ({} for {})", event.eventId(), event.eventType(), event.claimNumber());
            return;
        }
        Optional<AppUser> claimant = appUserRepository.findById(event.claimantId());
        if (claimant.isEmpty()) {
            log.warn("No user {} for event {} on {}; notification not stored",
                    event.claimantId(), event.eventId(), event.claimNumber());
            return;
        }
        notificationRepository.save(new Notification(claimant.get().getId(), claimant.get().getEmail(),
                event.claimNumber(), message.get(), Instant.now()));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> notificationsFor(String userId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(userId).stream()
                .map(n -> new NotificationResponse(n.getId(), n.getClaimNumber(), n.getMessage(), n.getCreatedAt()))
                .toList();
    }

    static Optional<String> messageFor(ClaimEvent event) {
        String claim = event.claimNumber();
        return Optional.ofNullable(switch (event.eventType()) {
            case ClaimSubmitted -> "We've received your claim %s. We'll be in touch once an officer has reviewed it."
                    .formatted(claim);
            case InformationRequested -> "We need more information about claim %s. Please open the claim and answer our question."
                    .formatted(claim);
            case ClaimApproved -> "Good news: your claim %s has been approved.".formatted(claim);
            case ClaimRejected -> "Your claim %s has been rejected. You can see the reason in the claim details."
                    .formatted(claim);
            case ClaimSettled -> "Your claim %s has been settled.".formatted(claim);
            case ClaimAssigned, InformationResponded, ReserveChanged -> null;
        });
    }
}
