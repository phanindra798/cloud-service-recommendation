package com.cloudrec.repository;

import com.cloudrec.entity.RecommendationHistory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class RecommendationHistoryRepositoryTest {

    @Autowired
    private RecommendationHistoryRepository repo;

    @Test
    void returnsNewestTwentyForUserOnly() {
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < 25; i++) {
            repo.save(entry("alice@example.com", "alice " + i, base.plusSeconds(i)));
        }
        repo.save(entry("bob@example.com", "bob", base.plusSeconds(100)));

        List<RecommendationHistory> result = repo.findTop20ByUserEmailOrderByCreatedAtDesc("alice@example.com");

        assertThat(result).hasSize(20);
        assertThat(result.get(0).getProblem()).isEqualTo("alice 24");
        assertThat(result.get(19).getProblem()).isEqualTo("alice 5");
        assertThat(result).allMatch(h -> h.getUserEmail().equals("alice@example.com"));
    }

    @Test
    void emptyForUserWithNoHistory() {
        assertThat(repo.findTop20ByUserEmailOrderByCreatedAtDesc("nobody@example.com")).isEmpty();
    }

    static RecommendationHistory entry(String email, String problem, Instant createdAt) {
        RecommendationHistory h = new RecommendationHistory();
        h.setUserEmail(email);
        h.setProblem(problem);
        h.setResultJson("{}");
        h.setCreatedAt(createdAt);
        return h;
    }
}
