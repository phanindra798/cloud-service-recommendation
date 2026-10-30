package com.cloudrec.controller;

import com.cloudrec.repository.RecommendationHistoryRepository;
import com.cloudrec.service.GeminiService;
import com.cloudrec.service.JwtService;
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
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
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
        when(gemini.getRecommendation(anyString())).thenReturn(new GeminiService.GeminiResult(
                List.of("Lambda", "DynamoDB"),
                "Serverless fits spiky traffic.",
                "graph TD; User-->Lambda; Lambda-->DynamoDB;",
                false));
    }

    @Test
    void recommendRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/recommend").contentType(MediaType.APPLICATION_JSON).content("{\"problem\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        verify(gemini, never()).getRecommendation(anyString());
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
                .andExpect(jsonPath("$.cost.aws").value(7.0))
                .andExpect(jsonPath("$.best").isNotEmpty())
                .andExpect(jsonPath("$.pricingSource.aws").value("static baseline"))
                .andExpect(jsonPath("$.reasoning").value("Serverless fits spiky traffic."))
                .andExpect(jsonPath("$.mermaid").value("graph TD; User-->Lambda; Lambda-->DynamoDB;"))
                .andExpect(jsonPath("$.aiGenerated").value(true));
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
