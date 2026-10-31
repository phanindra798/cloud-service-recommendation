package com.cloudrec.service;

import com.cloudrec.service.pricing.AppType;
import com.cloudrec.service.pricing.Region;
import com.cloudrec.service.pricing.Requirements;
import com.cloudrec.service.pricing.ServiceCatalog;
import com.cloudrec.service.pricing.UsageProfile;
import com.cloudrec.support.StubHttpServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private StubHttpServer server;
    private GeminiService gemini;

    @BeforeEach
    void setUp() throws Exception {
        server = new StubHttpServer();
        gemini = new GeminiService("test-key", server.url("/generate"), meters);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void parsesStructuredResponse() throws Exception {
        server.respond(200, geminiResponse(Map.of(
                "services", List.of("Lambda", "DynamoDB", "API Gateway"),
                "reasoning", "Serverless keeps costs low.",
                "mermaid", "graph TD; User-->APIGateway; APIGateway-->Lambda; Lambda-->DynamoDB;",
                "usage", Map.of("monthlyActiveUsers", 50_000, "requestsPerMonth", 30_000_000,
                        "storageGb", 200, "dataTransferGb", 1_500))));

        GeminiService.GeminiResult result = gemini.getRecommendation("a todo app");

        assertThat(result.fromFallback()).isFalse();
        assertThat(result.services()).containsExactly("Lambda", "DynamoDB", "API Gateway");
        assertThat(result.reasoning()).isEqualTo("Serverless keeps costs low.");
        assertThat(result.mermaid()).startsWith("graph TD");
        assertThat(result.usage()).isEqualTo(new UsageProfile(50_000, 30_000_000, 200, 1_500));
        assertThat(meters.find("gemini.failures").counters()).isEmpty();
    }

    @Test
    void sendsApiKeyPromptAndResponseSchema() throws Exception {
        server.respond(200, geminiResponse(Map.of("services", List.of("EC2"), "reasoning", "r", "mermaid", "m")));

        gemini.getRecommendation("a video streaming platform");

        assertThat(server.requests()).hasSize(1);
        StubHttpServer.RecordedRequest request = server.requests().get(0);
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.apiKeyHeader()).isEqualTo("test-key");

        JsonNode body = mapper.readTree(request.body());
        assertThat(body.at("/contents/0/parts/0/text").asText()).contains("a video streaming platform");
        assertThat(body.at("/generationConfig/responseMimeType").asText()).isEqualTo("application/json");
        List<String> allowed = mapper.convertValue(
                body.at("/generationConfig/responseSchema/properties/services/items/enum"), List.class);
        assertThat(allowed).containsExactlyElementsOf(ServiceCatalog.SUPPORTED_AWS_SERVICES);
        assertThat(body.at("/generationConfig/responseSchema/required").toString()).contains("usage");
        assertThat(body.at("/generationConfig/responseSchema/properties/usage/properties/requestsPerMonth/type").asText())
                .isEqualTo("INTEGER");
    }

    @Test
    void includesFormRequirementsInThePrompt() throws Exception {
        server.respond(200, geminiResponse(Map.of("services", List.of("EC2"), "reasoning", "r", "mermaid", "m")));

        gemini.getRecommendation("an online shop",
                new Requirements(Region.INDIA, 20_000L, AppType.ECOMMERCE, null, null, true));

        String prompt = mapper.readTree(server.requests().get(0).body()).at("/contents/0/parts/0/text").asText();
        assertThat(prompt).contains("users are mainly in India")
                .contains("about 20000 monthly active users")
                .contains("highly available");
    }

    @Test
    void dropsUnsupportedAndDuplicateServices() throws Exception {
        server.respond(200, geminiResponse(Map.of(
                "services", List.of("EC2", "Kinesis", "EC2", "S3"),
                "reasoning", "r",
                "mermaid", "m")));

        assertThat(gemini.getRecommendation("x").services()).containsExactly("EC2", "S3");
    }

    @Test
    void missingOrIncompleteUsageFallsBackToDefaultProfile() throws Exception {
        server.respond(200, geminiResponse(Map.of("services", List.of("EC2"), "reasoning", "r", "mermaid", "m",
                "usage", Map.of("monthlyActiveUsers", 5_000))));

        GeminiService.GeminiResult result = gemini.getRecommendation("x");

        assertThat(result.fromFallback()).isFalse();
        assertThat(result.usage()).isEqualTo(UsageProfile.DEFAULT);
    }

    @Test
    void absurdUsageEstimatesAreClamped() throws Exception {
        server.respond(200, geminiResponse(Map.of("services", List.of("EC2"), "reasoning", "r", "mermaid", "m",
                "usage", Map.of("monthlyActiveUsers", 0, "requestsPerMonth", 10,
                        "storageGb", 99_999_999_999L, "dataTransferGb", 50))));

        assertThat(gemini.getRecommendation("x").usage()).isEqualTo(new UsageProfile(1, 1_000, 10_000_000, 50));
    }

    @Test
    void fallsBackWhenNoSupportedServicesReturned() throws Exception {
        server.respond(200, geminiResponse(Map.of("services", List.of("Kinesis"), "reasoning", "r", "mermaid", "m")));

        assertFallback(gemini.getRecommendation("x"));
        assertThat(failures("no_services")).isEqualTo(1.0);
    }

    @Test
    void fallsBackOnHttpError() {
        server.respond(429, "{\"error\":{\"message\":\"quota exceeded\"}}");

        assertFallback(gemini.getRecommendation("x"));
        assertThat(failures("http_error")).isEqualTo(1.0);
    }

    @Test
    void fallsBackOnMalformedBody() {
        server.respond(200, "this is not json");

        assertFallback(gemini.getRecommendation("x"));
        assertThat(failures("exception")).isEqualTo(1.0);
    }

    @Test
    void fallsBackWhenApiUnreachable() {
        GeminiService unreachable = new GeminiService("k", "http://127.0.0.1:1/generate", meters);

        assertFallback(unreachable.getRecommendation("x"));
        assertThat(failures("exception")).isEqualTo(1.0);
    }

    private double failures(String reason) {
        return meters.get("gemini.failures").tag("reason", reason).counter().count();
    }

    private void assertFallback(GeminiService.GeminiResult result) {
        assertThat(result.fromFallback()).isTrue();
        assertThat(result.services()).containsExactly("EC2", "S3", "RDS", "CloudFront");
        assertThat(result.mermaid()).startsWith("graph TD");
        assertThat(result.usage()).isEqualTo(UsageProfile.DEFAULT);
    }

    /** Wraps the model's JSON output the way the Gemini generateContent API does. */
    private String geminiResponse(Map<String, Object> modelJson) throws Exception {
        String text = mapper.writeValueAsString(modelJson);
        return mapper.writeValueAsString(Map.of(
                "candidates", List.of(Map.of(
                        "content", Map.of("parts", List.of(Map.of("text", text)))))));
    }
}
