package com.chubb.claims.notification;

import com.chubb.claims.common.AppUser;
import com.chubb.claims.common.AppUserRepository;
import com.chubb.claims.common.ClaimEvent;
import com.chubb.claims.common.ClaimEventType;
import com.chubb.claims.common.Market;
import com.chubb.claims.common.ProcessedEventStore;
import com.chubb.claims.common.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository notificationRepository;
    @Mock AppUserRepository appUserRepository;
    @Mock ProcessedEventStore processedEvents;
    @InjectMocks NotificationService service;

    static ClaimEvent event(ClaimEventType type) {
        return new ClaimEvent(UUID.randomUUID(), type, Instant.now(), "CLM-SG-2026-000005", 1, "SUBMITTED",
                Market.SG, "SGD", "MOTOR", new BigDecimal("1000.00"), null, "C001", Instant.now());
    }

    @Test
    void sameEventDeliveredTwiceCreatesOneNotification() {
        ClaimEvent event = event(ClaimEventType.InformationRequested);
        // The real store returns true the first time and false for the duplicate (unique key in processed_event).
        when(processedEvents.markProcessed(event.eventId(), NotificationService.CONSUMER_GROUP)).thenReturn(true, false);
        when(appUserRepository.findById("C001"))
                .thenReturn(Optional.of(new AppUser("C001", "Mei Ling", "meiling@example.com", UserRole.CLAIMANT, null)));

        service.handle(event);
        service.handle(event);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(saved.capture());
        assertEquals("C001", saved.getValue().getRecipientId());
        assertEquals("meiling@example.com", saved.getValue().getRecipientEmail());
        assertEquals("We need more information about claim CLM-SG-2026-000005. Please open the claim and answer our question.",
                saved.getValue().getMessage());
    }

    @Test
    void eventsClaimantsAreNotToldAboutAreIgnored() {
        for (ClaimEventType type : new ClaimEventType[]{
                ClaimEventType.ClaimAssigned, ClaimEventType.InformationResponded, ClaimEventType.ReserveChanged}) {
            service.handle(event(type));
        }
        verifyNoInteractions(processedEvents, notificationRepository);
    }

    @Test
    void notifiedEventTypesAllHaveAMessage() {
        for (ClaimEventType type : new ClaimEventType[]{ClaimEventType.ClaimSubmitted, ClaimEventType.InformationRequested,
                ClaimEventType.ClaimApproved, ClaimEventType.ClaimRejected, ClaimEventType.ClaimSettled}) {
            when(processedEvents.markProcessed(any(), any())).thenReturn(true);
            when(appUserRepository.findById("C001"))
                    .thenReturn(Optional.of(new AppUser("C001", "Mei Ling", "m@example.com", UserRole.CLAIMANT, null)));
            service.handle(event(type));
        }
        verify(notificationRepository, times(5)).save(any());
    }
}
