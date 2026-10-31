package com.cloudrec;

import com.cloudrec.entity.User;
import com.cloudrec.repository.RecommendationHistoryRepository;
import com.cloudrec.repository.UserRepository;
import com.cloudrec.service.GeminiService;
import com.cloudrec.service.pricing.UsageProfile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end flow against a real PostgreSQL 16 started by Testcontainers:
 * register → login → recommend → history. Skipped automatically when Docker
 * isn't available (it always runs in CI).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PostgresIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private RecommendationHistoryRepository history;

    @MockitoBean
    private GeminiService gemini;

    @Test
    void fullUserJourney() throws Exception {
        when(gemini.getRecommendation(anyString())).thenReturn(new GeminiService.GeminiResult(
                List.of("EC2", "RDS"), "Classic two-tier app.", "graph TD; User-->EC2; EC2-->RDS;",
                UsageProfile.DEFAULT, false));

        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"pg@example.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk());

        String loginJson = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"pg@example.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = loginJson.replaceAll(".*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mvc.perform(post("/api/recommend").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"problem\":\"an online bookstore\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aws[0]").value("EC2"))
                .andExpect(jsonPath("$.cost.aws").value(45.37));

        mvc.perform(get("/api/history").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].problem").value("an online bookstore"));

        assertThat(history.findTop20ByUserEmailOrderByCreatedAtDesc("pg@example.com"))
                .singleElement()
                .satisfies(h -> assertThat(h.getResultJson()).contains("\"reasoning\":\"Classic two-tier app.\""));
    }

    @Test
    void emailIsUniqueAtTheDatabaseLevel() {
        users.saveAndFlush(user("unique@example.com"));

        assertThatThrownBy(() -> users.saveAndFlush(user("unique@example.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private User user(String email) {
        User u = new User();
        u.setEmail(email);
        u.setPassword("hash");
        return u;
    }
}
