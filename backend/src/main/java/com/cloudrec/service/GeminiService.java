package com.cloudrec.service;

import com.cloudrec.service.pricing.ServiceCatalog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Calls the Gemini API in structured-output mode (responseSchema) so the
 * model returns typed JSON instead of free text. Falls back to a baseline
 * architecture when the API is unreachable or returns an error.
 */
@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    private final String apiKey;
    private final String apiUrl;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public GeminiService(
            @Value("${gemini.api.key}") String apiKey,
            @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent}") String apiUrl) {
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
    }

    public record GeminiResult(List<String> services, String reasoning, String mermaid, boolean fromFallback) {}

    public GeminiResult getRecommendation(String input) {
        try {
            List<String> supported = ServiceCatalog.SUPPORTED_AWS_SERVICES;

            String prompt = """
                    You are an expert cloud solutions architect.
                    A user describes their application requirement below. Recommend AWS services.

                    Rules:
                    - Pick only from this list: %s
                    - "reasoning": 3-6 sentences explaining WHY each chosen service fits the requirement.
                    - "mermaid": a valid Mermaid flowchart (graph TD) showing how the chosen services connect
                      for this architecture. Node labels must contain only letters, numbers and spaces.
                      Example format: graph TD; User-->CloudFront; CloudFront-->EC2; EC2-->RDS;

                    User requirement: %s
                    """.formatted(String.join(", ", supported), input);

            ObjectNode body = mapper.createObjectNode();
            ArrayNode contents = body.putArray("contents");
            contents.addObject().putArray("parts").addObject().put("text", prompt);

            ObjectNode genConfig = body.putObject("generationConfig");
            genConfig.put("responseMimeType", "application/json");
            ObjectNode schema = genConfig.putObject("responseSchema");
            schema.put("type", "OBJECT");
            ObjectNode props = schema.putObject("properties");
            ObjectNode servicesProp = props.putObject("services");
            servicesProp.put("type", "ARRAY");
            ObjectNode items = servicesProp.putObject("items");
            items.put("type", "STRING");
            ArrayNode enumValues = items.putArray("enum");
            supported.forEach(enumValues::add);
            props.putObject("reasoning").put("type", "STRING");
            props.putObject("mermaid").put("type", "STRING");
            schema.putArray("required").add("services").add("reasoning").add("mermaid");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Gemini returned HTTP {}, using fallback: {}", response.statusCode(), abbreviate(response.body()));
                return fallback();
            }

            JsonNode root = mapper.readTree(response.body());
            String jsonText = root.path("candidates").path(0)
                    .path("content").path("parts").path(0)
                    .path("text").asText();

            JsonNode result = mapper.readTree(jsonText);

            List<String> services = new ArrayList<>();
            result.path("services").forEach(s -> {
                String name = s.asText();
                if (supported.contains(name) && !services.contains(name)) {
                    services.add(name);
                }
            });
            if (services.isEmpty()) {
                return fallback();
            }

            return new GeminiResult(
                    services,
                    result.path("reasoning").asText(""),
                    result.path("mermaid").asText(""),
                    false
            );

        } catch (Exception e) {
            log.warn("Gemini call failed, using fallback: {}", e.toString());
            return fallback();
        }
    }

    private static String abbreviate(String s) {
        return s.length() <= 500 ? s : s.substring(0, 500) + "…";
    }

    private GeminiResult fallback() {
        return new GeminiResult(
                List.of("EC2", "S3", "RDS", "CloudFront"),
                "AI service is currently unavailable. Showing a baseline web application "
                        + "architecture: EC2 for compute, S3 for object storage, RDS for the "
                        + "relational database and CloudFront as the CDN.",
                "graph TD; User-->CloudFront; CloudFront-->EC2; EC2-->RDS; EC2-->S3;",
                true
        );
    }
}
