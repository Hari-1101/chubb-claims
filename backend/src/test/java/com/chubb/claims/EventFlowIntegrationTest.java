package com.chubb.claims;

import com.chubb.claims.common.ClaimEvent;
import com.chubb.claims.common.ClaimEventType;
import com.chubb.claims.common.Market;
import com.chubb.claims.notification.NotificationRepository;
import com.chubb.claims.notification.NotificationService;
import com.chubb.claims.reporting.ReportingClaimRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end through real Kafka and Postgres (docker compose must be up): API → commit → Kafka → consumers.
 * NOT transactional: events are only sent after a real commit, so this test leaves its claims in the dev database.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EventFlowIntegrationTest {

    private static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(30);

    @Autowired MockMvc mvc;
    @Autowired ReportingClaimRepository reportingClaims;
    @Autowired NotificationService notificationService;
    @Autowired NotificationRepository notifications;
    @Autowired JdbcTemplate jdbc;

    private static MockHttpServletRequestBuilder as(String userId, String role, MockHttpServletRequestBuilder request) {
        return request.header("X-User-Id", userId).header("X-User-Role", role).contentType(MediaType.APPLICATION_JSON);
    }

    private String body(MockHttpServletRequestBuilder request, String userId, String role) throws Exception {
        return mvc.perform(as(userId, role, request)).andReturn().getResponse().getContentAsString();
    }

    @Test
    void submitAndAssignShowUpInNotificationsAndReports() throws Exception {
        // Claimant C003 submits on their Hong Kong property policy; Property officer S003 picks it up.
        String submitted = mvc.perform(as("C003", "CLAIMANT", post("/api/claims").content("""
                        {"policyNumber": "POL-HK-PRP-001", "incidentDate": "%s",
                         "description": "Water leak from upstairs flat", "estimatedAmount": 64000.00}
                        """.formatted(Market.HK.today().minusDays(1)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String claimNumber = JsonPath.read(submitted, "$.claimNumber");

        mvc.perform(as("S003", "STAFF", post("/api/staff/claims/" + claimNumber + "/assign")))
                .andExpect(status().isOk());

        // 1. Notification for the claimant (from ClaimSubmitted).
        await().atMost(ASYNC_TIMEOUT).untilAsserted(() ->
                mvc.perform(as("C003", "CLAIMANT", get("/api/me/notifications")))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$[*].message", hasItem(
                                "We've received your claim " + claimNumber
                                        + ". We'll be in touch once an officer has reviewed it."))));

        // 2. Reporting read model has caught up with the assignment (version 1).
        await().atMost(ASYNC_TIMEOUT).until(() -> reportingClaims.findById(claimNumber)
                .filter(r -> r.getLastClaimVersion() >= 1 && "S003".equals(r.getAssignedOfficerId()))
                .isPresent());

        // 3. Exposure for HK/HKD matches the read model exactly, including this claim.
        Map<String, Object> expected = jdbc.queryForMap("""
                SELECT count(*) AS claims, sum(reserve_amount) AS total FROM reporting_claim
                WHERE market = 'HK' AND currency = 'HKD' AND status NOT IN ('SETTLED', 'REJECTED')""");
        String exposure = body(get("/api/staff/reports/exposure"), "S003", "STAFF");
        List<Map<String, Object>> hk = JsonPath.read(exposure, "$.exposures[?(@.market == 'HK' && @.currency == 'HKD')]");
        assertEquals(1, hk.size());
        assertEquals(((Number) expected.get("claims")).longValue(), ((Number) hk.get(0).get("openClaims")).longValue());
        assertEquals(0, new BigDecimal(expected.get("total").toString())
                .compareTo(new BigDecimal(hk.get(0).get("totalReserve").toString())));
        assertTrue(new BigDecimal(hk.get(0).get("totalReserve").toString()).compareTo(new BigDecimal("64000.00")) >= 0);
        assertTrue(JsonPath.read(exposure, "$.lastUpdated") != null);

        // 4. Workload for the Property team shows S003 with this claim under review.
        long s003Open = jdbc.queryForObject("""
                SELECT count(*) FROM reporting_claim
                WHERE assigned_officer_id = 'S003' AND status NOT IN ('SETTLED', 'REJECTED')""", Long.class);
        String workload = body(get("/api/staff/reports/workload").param("team", "Property"), "S003", "STAFF");
        List<Number> openForS003 = JsonPath.read(workload, "$.officers[?(@.officerId == 'S003')].openClaims");
        assertEquals(s003Open, openForS003.get(0).longValue());
        List<Number> underReview = JsonPath.read(workload,
                "$.officers[?(@.officerId == 'S003')].openClaimsByStatus.UNDER_REVIEW");
        assertTrue(underReview.get(0).longValue() >= 1);
        List<String> teams = JsonPath.read(workload, "$.officers[*].team");
        assertTrue(teams.stream().allMatch("Property"::equals), "team filter applied");
    }

    @Test
    void duplicateDeliveryAgainstTheRealDatabaseCreatesOneNotification() {
        String claimNumber = "CLM-DUP-" + UUID.randomUUID().toString().substring(0, 8);
        ClaimEvent event = new ClaimEvent(UUID.randomUUID(), ClaimEventType.ClaimApproved, Instant.now(), claimNumber,
                3, "APPROVED", Market.SG, "SGD", "MOTOR", new BigDecimal("100.00"), "S001", "C001", Instant.now());

        notificationService.handle(event);
        notificationService.handle(event);

        assertEquals(1, notifications.countByClaimNumber(claimNumber));
    }
}
