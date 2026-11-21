package com.chubb.claims;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test: the Spring context starts. Requires the docker-compose infra (Postgres, Kafka) to be running.
 */
@SpringBootTest
@ActiveProfiles("test") // claims_test database, claims.events.test topic (application-test.yml)
class ClaimsApplicationTests {

    @Test
    void contextLoads() {
    }
}
