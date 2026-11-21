package com.chubb.claims.reporting;

import com.chubb.claims.common.ClaimEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Kafka entry point of the reporting module. All logic is in {@link ReportingProjection}. */
@Component
class ReportingEventListener {

    private final ReportingProjection projection;

    ReportingEventListener(ReportingProjection projection) {
        this.projection = projection;
    }

    @KafkaListener(topics = ClaimEvent.TOPIC, groupId = ReportingProjection.CONSUMER_GROUP)
    void on(ClaimEvent event) {
        projection.handle(event);
    }
}
