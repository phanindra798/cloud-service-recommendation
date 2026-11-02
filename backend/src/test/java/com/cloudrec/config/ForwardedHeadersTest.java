package com.cloudrec.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behind nginx every request arrives from the proxy's address. With
 * forward-headers-strategy=native (set by docker-compose) Tomcat takes the
 * client IP from X-Forwarded-For, so the per-IP login limit applies per
 * visitor instead of to everyone at once. Needs a real server: MockMvc
 * doesn't run Tomcat's RemoteIpValve.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.forward-headers-strategy=native",
        "security.auth.max-requests-per-minute=3"
})
class ForwardedHeadersTest {

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void perIpLimitUsesTheForwardedClientAddress() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(login("203.0.113.7")).isEqualTo(401);
        }
        assertThat(login("203.0.113.7")).as("4th request from the same client").isEqualTo(429);

        // A different visitor behind the same proxy is unaffected
        assertThat(login("198.51.100.9")).isEqualTo(401);
    }

    private int login(String clientIp) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/auth/login"))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", clientIp)
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"nobody@example.com\",\"password\":\"wrong-pass\"}"))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
