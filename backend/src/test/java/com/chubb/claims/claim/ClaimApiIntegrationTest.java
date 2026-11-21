package com.chubb.claims.claim;

import com.chubb.claims.common.Market;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end through HTTP, services and the real Postgres from docker compose (run `docker compose up -d` first).
 * Uses seeded users/policies. Each test runs in a transaction that is rolled back, so the dev database stays clean.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ClaimApiIntegrationTest {

    private static final String CLAIMANT = "C001";
    private static final String OFFICER = "S001";

    @Autowired
    MockMvc mvc;

    private static MockHttpServletRequestBuilder as(String userId, String role, MockHttpServletRequestBuilder request) {
        return request.header("X-User-Id", userId).header("X-User-Role", role).contentType(MediaType.APPLICATION_JSON);
    }

    private ResultActions claimant(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(as(CLAIMANT, "CLAIMANT", request));
    }

    private ResultActions officer(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(as(OFFICER, "STAFF", request));
    }

    private String submitClaim() throws Exception {
        String body = """
                {"policyNumber": "POL-SG-MOT-001", "incidentDate": "%s",
                 "description": "Rear-ended at a traffic light on Orchard Road", "estimatedAmount": 8500.00}
                """.formatted(Market.SG.today().minusDays(2));
        String response = claimant(post("/api/claims").content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.market").value("SG"))
                .andExpect(jsonPath("$.currency").value("SGD"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.claimNumber");
    }

    @Test
    void happyPathFromSubmissionToSettlement() throws Exception {
        String claimNumber = submitClaim();
        String staffUrl = "/api/staff/claims/" + claimNumber;

        officer(post(staffUrl + "/assign"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.assignedOfficerId").value(OFFICER));

        officer(get("/api/staff/claims").param("assigned", "me").param("status", "UNDER_REVIEW").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].claimNumber", hasItem(claimNumber)));

        String afterRequest = officer(post(staffUrl + "/information-requests")
                .content("{\"question\": \"Please upload the police report number.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INFO_REQUESTED"))
                .andReturn().getResponse().getContentAsString();
        Number requestId = JsonPath.read(afterRequest, "$.informationRequests[0].id");

        claimant(post("/api/claims/" + claimNumber + "/information-requests/" + requestId + "/response")
                .content("{\"response\": \"Police report SPF-2026-1234.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));

        officer(post(staffUrl + "/approve").content("{\"reason\": \"Covered, third party at fault\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        officer(post(staffUrl + "/settle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"));

        claimant(get("/api/claims/" + claimNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"))
                .andExpect(jsonPath("$.statusHistory[*].toStatus",
                        contains("SUBMITTED", "UNDER_REVIEW", "INFO_REQUESTED", "UNDER_REVIEW", "APPROVED", "SETTLED")))
                .andExpect(jsonPath("$.statusHistory[*].changedBy",
                        contains(CLAIMANT, OFFICER, OFFICER, CLAIMANT, OFFICER, OFFICER)))
                .andExpect(jsonPath("$.informationRequests[0].response").value("Police report SPF-2026-1234."));
    }

    @Test
    void invalidTransitionReturns409() throws Exception {
        String claimNumber = submitClaim();
        officer(post("/api/staff/claims/" + claimNumber + "/assign")).andExpect(status().isOk());

        officer(post("/api/staff/claims/" + claimNumber + "/settle"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Invalid status transition"))
                .andExpect(jsonPath("$.fromStatus").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.toStatus").value("SETTLED"));
    }

    @Test
    void identityAndAccessErrors() throws Exception {
        mvc.perform(get("/api/claims/mine")).andExpect(status().isUnauthorized());
        mvc.perform(as(CLAIMANT, "STAFF", get("/api/staff/claims"))).andExpect(status().isUnauthorized());
        mvc.perform(as("NOBODY", "CLAIMANT", get("/api/claims/mine"))).andExpect(status().isUnauthorized());
        claimant(get("/api/staff/claims")).andExpect(status().isForbidden());

        String claimNumber = submitClaim();
        mvc.perform(as("C002", "CLAIMANT", get("/api/claims/" + claimNumber))).andExpect(status().isNotFound());
    }

    @Test
    void validationErrorsListTheFields() throws Exception {
        claimant(post("/api/claims").content("{\"policyNumber\": \"\", \"estimatedAmount\": -5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("policyNumber")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("estimatedAmount")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("incidentDate")));
    }
}
