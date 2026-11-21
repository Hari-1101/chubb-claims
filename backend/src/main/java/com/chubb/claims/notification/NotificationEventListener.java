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

    // Topic and group suffix come from properties, so integration tests (profile "test") use their own topic and groups.
    @KafkaListener(topics = "${claims.events.topic}",
            groupId = NotificationService.CONSUMER_GROUP + "${claims.events.group-suffix:}")
    void on(ClaimEvent event) {
        notificationService.handle(event);
    }
}
