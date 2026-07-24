package com.cloudrec.controller;

import com.cloudrec.dto.RecommendRequest;
import com.cloudrec.entity.RecommendationHistory;
import com.cloudrec.repository.RecommendationHistoryRepository;
import com.cloudrec.service.RateLimiterService;
import com.cloudrec.service.RecommendationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class RecommendationController {

    private final RecommendationService service;
    private final RecommendationHistoryRepository historyRepo;
    private final RateLimiterService rateLimiter;
    private final ObjectMapper mapper = new ObjectMapper();

    public RecommendationController(RecommendationService service,
                                    RecommendationHistoryRepository historyRepo,
                                    RateLimiterService rateLimiter) {
        this.service = service;
        this.historyRepo = historyRepo;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/recommend")
    public ResponseEntity<?> recommend(@RequestBody RecommendRequest request, Authentication auth) {

        String problem = request.getProblem() == null ? "" : request.getProblem().trim();
        if (problem.isEmpty()) {
            throw new IllegalArgumentException("Problem description must not be empty");
        }
        if (problem.length() > 2000) {
            throw new IllegalArgumentException("Problem description too long (max 2000 characters)");
        }

        String email = auth.getName();
        if (!rateLimiter.allow(email)) {
            return ResponseEntity.status(429)
                    .body(Map.of("error", "Rate limit exceeded: max 10 requests per minute"));
        }

        Map<String, Object> result = service.getRecommendation(problem);

        try {
            RecommendationHistory entry = new RecommendationHistory();
            entry.setUserEmail(email);
            entry.setProblem(problem);
            entry.setResultJson(mapper.writeValueAsString(result));
            entry.setCreatedAt(Instant.now());
            historyRepo.save(entry);
        } catch (Exception e) {
            System.out.println("Failed to save history: " + e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/history")
    public ResponseEntity<List<RecommendationHistory>> history(Authentication auth) {
        return ResponseEntity.ok(
                historyRepo.findTop20ByUserEmailOrderByCreatedAtDesc(auth.getName()));
    }
}
