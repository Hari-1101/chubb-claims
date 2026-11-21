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

    // Topic and group suffix come from properties, so integration tests (profile "test") use their own topic and groups.
    @KafkaListener(topics = "${claims.events.topic}",
            groupId = ReportingProjection.CONSUMER_GROUP + "${claims.events.group-suffix:}")
    void on(ClaimEvent event) {
        projection.handle(event);
    }
}
