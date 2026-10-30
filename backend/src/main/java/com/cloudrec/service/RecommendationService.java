package com.cloudrec.service;

import com.cloudrec.service.pricing.CostService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class RecommendationService {

    private final GeminiService geminiService;
    private final CostService costService;
    private final MeterRegistry meters;

    public RecommendationService(GeminiService geminiService, CostService costService, MeterRegistry meters) {
        this.geminiService = geminiService;
        this.costService = costService;
        this.meters = meters;
    }

    public Map<String, Object> getRecommendation(String input) {
        Timer.Sample sample = Timer.start(meters);

        GeminiService.GeminiResult result = geminiService.getRecommendation(input);

        Map<String, Object> response = new HashMap<>(costService.process(result.services()));
        response.put("reasoning", result.reasoning());
        response.put("mermaid", result.mermaid());
        response.put("aiGenerated", !result.fromFallback());

        // End-to-end latency (AI + pricing), tagged by whether the AI answered
        sample.stop(meters.timer("recommendation.latency", "source", result.fromFallback() ? "fallback" : "ai"));
        return response;
    }
}
