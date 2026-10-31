package com.cloudrec.controller;

import com.cloudrec.repository.RecommendationHistoryRepository;
import com.cloudrec.service.GeminiService;
import com.cloudrec.service.JwtService;
import com.cloudrec.service.pricing.AppType;
import com.cloudrec.service.pricing.Region;
import com.cloudrec.service.pricing.UsageProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RecommendationControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtService jwt;
    @Autowired
    private RecommendationHistoryRepository history;

    @MockitoBean
    private GeminiService gemini;

    @BeforeEach
    void setUp() {
        history.deleteAll();
        when(gemini.getRecommendation(anyString(), any())).thenReturn(new GeminiService.GeminiResult(
                List.of("Lambda", "DynamoDB"),
                "Serverless fits spiky traffic.",
                "graph TD; User-->Lambda; Lambda-->DynamoDB;",
                new UsageProfile(10_000, 2_000_000, 50, 100),
                false));
    }

    @Test
    void recommendRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/recommend").contentType(MediaType.APPLICATION_JSON).content("{\"problem\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        verify(gemini, never()).getRecommendation(anyString(), any());
    }

    @Test
    void invalidTokenIsRejected() throws Exception {
        mvc.perform(post("/api/recommend").header("Authorization", "Bearer not-a-real-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"problem\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void historyRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/history")).andExpect(status().isUnauthorized());
    }

    @Test
    void returnsServicesCostsReasoningAndDiagram() throws Exception {
        recommend("alice@example.com", "a serverless todo app")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aws[0]").value("Lambda"))
                .andExpect(jsonPath("$.azure[1]").value("Cosmos DB"))
                .andExpect(jsonPath("$.gcp[1]").value("Firestore"))
                .andExpect(jsonPath("$.cost.aws").value(3.06)) // 2M req × (1.03 Lambda + 0.50 DynamoDB)
                .andExpect(jsonPath("$.breakdown.aws[0].service").value("Lambda"))
                .andExpect(jsonPath("$.breakdown.aws[0].quantity").value(2.0))
                .andExpect(jsonPath("$.breakdown.aws[0].monthlyCost").value(2.06))
                .andExpect(jsonPath("$.usage.monthlyActiveUsers").value(10_000))
                .andExpect(jsonPath("$.best").isNotEmpty())
                .andExpect(jsonPath("$.pricingSource.aws").value("list-price estimate"))
                .andExpect(jsonPath("$.reasoning").value("Serverless fits spiky traffic."))
                .andExpect(jsonPath("$.mermaid").value("graph TD; User-->Lambda; Lambda-->DynamoDB;"))
                .andExpect(jsonPath("$.aiGenerated").value(true));
    }

    @Test
    void formRequirementsShapeTheEstimate() throws Exception {
        mvc.perform(post("/api/recommend")
                        .header("Authorization", bearer("alice@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"problem":"an online shop","region":"india","monthlyActiveUsers":100000,
                                 "appType":"ECOMMERCE","highAvailability":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.region.label").value("India"))
                .andExpect(jsonPath("$.region.aws").value("ap-south-1"))
                .andExpect(jsonPath("$.highAvailability").value(true))
                .andExpect(jsonPath("$.usage.monthlyActiveUsers").value(100_000))
                .andExpect(jsonPath("$.usage.requestsPerMonth").value(40_000_000)) // 400 req/user for e-commerce
                .andExpect(jsonPath("$.usage.appType").value("E-commerce store"))
                .andExpect(jsonPath("$.usage.sources.monthlyActiveUsers").value("you"))
                .andExpect(jsonPath("$.usage.sources.requestsPerMonth").value("app type"));

        verify(gemini).getRecommendation(eq("an online shop"), argThat(r ->
                r.region() == Region.INDIA && r.appType() == AppType.ECOMMERCE && r.highAvailability()));
    }

    @Test
    void unknownRegionIsBadRequest() throws Exception {
        mvc.perform(post("/api/recommend").header("Authorization", bearer("alice@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"problem\":\"x\",\"region\":\"MARS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unknown region: MARS"));
    }

    @Test
    void outOfRangeUserCountIsBadRequest() throws Exception {
        mvc.perform(post("/api/recommend").header("Authorization", bearer("alice@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"problem\":\"x\",\"monthlyActiveUsers\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("monthlyActiveUsers must be between 1 and 1000000000"));
        verify(gemini, never()).getRecommendation(anyString(), any());
    }

    @Test
    void savesHistoryPerUser() throws Exception {
        recommend("alice@example.com", "first idea").andExpect(status().isOk());
        recommend("alice@example.com", "second idea").andExpect(status().isOk());
        recommend("bob@example.com", "bob's idea").andExpect(status().isOk());

        mvc.perform(get("/api/history").header("Authorization", bearer("alice@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].problem").value("second idea"))
                .andExpect(jsonPath("$[1].problem").value("first idea"));

        assertThat(history.findAll()).hasSize(3);
    }

    @Test
    void emptyProblemIsBadRequest() throws Exception {
        recommend("alice@example.com", "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Problem description must not be empty"));
    }

    @Test
    void tooLongProblemIsBadRequest() throws Exception {
        recommend("alice@example.com", "a".repeat(2001))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Problem description too long (max 2000 characters)"));
    }

    @Test
    void eleventhRequestInAMinuteIsRateLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            recommend("ratelimited@example.com", "idea " + i).andExpect(status().isOk());
        }
        recommend("ratelimited@example.com", "one too many")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Rate limit exceeded: max 10 requests per minute"));
    }

    @Test
    void unknownPathIs404Not500() throws Exception {
        mvc.perform(get("/api/does-not-exist").header("Authorization", bearer("alice@example.com")))
                .andExpect(status().isNotFound());
    }

    @Test
    void wrongMethodIs405() throws Exception {
        mvc.perform(get("/api/recommend").header("Authorization", bearer("alice@example.com")))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void metricsRequireAuthentication() throws Exception {
        mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
    }

    @Test
    void recordsRecommendationLatencyAndRateLimitRejections() throws Exception {
        for (int i = 0; i < 11; i++) {
            recommend("metrics@example.com", "idea " + i);
        }

        mvc.perform(get("/actuator/metrics/recommendation.latency").param("tag", "source:ai")
                        .header("Authorization", bearer("metrics@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements[?(@.statistic == 'COUNT')].value").value(hasItem(greaterThanOrEqualTo(10.0))));

        mvc.perform(get("/actuator/metrics/ratelimit.rejections")
                        .header("Authorization", bearer("metrics@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements[0].value").value(greaterThanOrEqualTo(1.0)));
    }

    private ResultActions recommend(String email, String problem) throws Exception {
        return mvc.perform(post("/api/recommend")
                .header("Authorization", bearer(email))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"problem\":\"%s\"}".formatted(problem)));
    }

    private String bearer(String email) {
        return "Bearer " + jwt.generateToken(email);
    }
}
