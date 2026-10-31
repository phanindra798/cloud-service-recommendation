package com.cloudrec.service;

import com.cloudrec.service.pricing.CostService;
import com.cloudrec.service.pricing.Requirements;
import com.cloudrec.service.pricing.UsageResolver;
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

    public Map<String, Object> getRecommendation(String input, Requirements requirements) {
        Timer.Sample sample = Timer.start(meters);

        GeminiService.GeminiResult result = geminiService.getRecommendation(input, requirements);
        UsageResolver.Resolved resolved = UsageResolver.resolve(requirements, result.usage());

        Map<String, Object> response = new HashMap<>(costService.process(result.services(), resolved.usage(),
                requirements.regionOrDefault(), requirements.highAvailability()));
        @SuppressWarnings("unchecked")
        Map<String, Object> usage = (Map<String, Object>) response.get("usage");
        usage.put("sources", resolved.sources());
        if (requirements.appType() != null) {
            usage.put("appType", requirements.appType().label());
        }
        response.put("reasoning", result.reasoning());
        response.put("mermaid", result.mermaid());
        response.put("aiGenerated", !result.fromFallback());

        // End-to-end latency (AI + pricing), tagged by whether the AI answered
        sample.stop(meters.timer("recommendation.latency", "source", result.fromFallback() ? "fallback" : "ai"));
        return response;
    }
}
