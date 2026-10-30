package com.cloudrec;

import com.cloudrec.service.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Production log format: one ECS JSON object per line, carrying requestId and user from the MDC. */
@SpringBootTest(properties = "logging.structured.format.console=ecs")
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class StructuredLoggingTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtService jwt;

    @Test
    void accessLogIsJsonWithRequestIdUserAndHttpFields(CapturedOutput output) throws Exception {
        mvc.perform(get("/api/history")
                        .header("Authorization", "Bearer " + jwt.generateToken("alice@example.com"))
                        .header("X-Request-Id", "req-42"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "req-42"));

        List<JsonNode> lines = output.getOut().lines()
                .filter(l -> l.startsWith("{"))
                .map(this::parse)
                .filter(n -> "req-42".equals(n.path("requestId").asText()))
                .toList();

        assertThat(lines).isNotEmpty();
        JsonNode access = lines.get(lines.size() - 1);
        assertThat(access.at("/log/level").asText()).isEqualTo("INFO");
        assertThat(access.path("user").asText()).isEqualTo("alice@example.com");
        assertThat(access.path("message").asText()).isEqualTo("GET /api/history -> 200 in "
                + access.path("durationMs").asText() + " ms");
        assertThat(access.at("/http/method").asText()).isEqualTo("GET");
        assertThat(access.at("/http/path").asText()).isEqualTo("/api/history");
        assertThat(access.at("/http/status").asInt()).isEqualTo(200);
    }

    private JsonNode parse(String line) {
        try {
            return mapper.readTree(line);
        } catch (Exception e) {
            throw new AssertionError("Not JSON: " + line, e);
        }
    }
}
