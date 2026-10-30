package com.cloudrec.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "test-secret-that-is-at-least-32-bytes-long!!";

    private final JwtService jwt = new JwtService(SECRET, 60_000);

    @Test
    void generatedTokenRoundTripsToEmail() {
        String token = jwt.generateToken("alice@example.com");

        assertThat(jwt.validateAndGetEmail(token)).isEqualTo("alice@example.com");
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwt.generateToken("alice@example.com");
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");

        assertThat(jwt.validateAndGetEmail(tampered)).isNull();
    }

    @Test
    void tokenSignedWithDifferentSecretIsRejected() {
        JwtService other = new JwtService("a-completely-different-secret-of-32-bytes!!", 60_000);
        String token = other.generateToken("alice@example.com");

        assertThat(jwt.validateAndGetEmail(token)).isNull();
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService shortLived = new JwtService(SECRET, -1_000);
        String token = shortLived.generateToken("alice@example.com");

        assertThat(jwt.validateAndGetEmail(token)).isNull();
    }

    @Test
    void garbageIsRejected() {
        assertThat(jwt.validateAndGetEmail("not-a-jwt")).isNull();
        assertThat(jwt.validateAndGetEmail("")).isNull();
    }
}
