package com.cloudrec.service;

import com.cloudrec.service.pricing.CostService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class RecommendationService {

    private final GeminiService geminiService;
    private final CostService costService;

    public RecommendationService(GeminiService geminiService, CostService costService) {
        this.geminiService = geminiService;
        this.costService = costService;
    }

    public Map<String, Object> getRecommendation(String input) {

        GeminiService.GeminiResult result = geminiService.getRecommendation(input);

        Map<String, Object> response = new HashMap<>(costService.process(result.services()));
        response.put("reasoning", result.reasoning());
        response.put("mermaid", result.mermaid());
        response.put("aiGenerated", !result.fromFallback());

        return response;
    }
}
