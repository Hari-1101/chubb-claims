package com.chubb.claims;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test: the Spring context starts. Requires the docker-compose infra (Postgres, Kafka) to be running.
 */
@SpringBootTest
class ClaimsApplicationTests {

    @Test
    void contextLoads() {
    }
}
