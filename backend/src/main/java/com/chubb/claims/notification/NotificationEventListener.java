package com.chubb.claims.notification;

import com.chubb.claims.common.ClaimEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Kafka entry point of the notification module. All logic is in {@link NotificationService}. */
@Component
class NotificationEventListener {

    private final NotificationService notificationService;

    NotificationEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = ClaimEvent.TOPIC, groupId = NotificationService.CONSUMER_GROUP)
    void on(ClaimEvent event) {
        notificationService.handle(event);
    }
}
