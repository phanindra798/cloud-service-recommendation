package com.cloudrec.controller;

import com.cloudrec.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;

    @BeforeEach
    void cleanDatabase() {
        users.deleteAll();
    }

    @Test
    void registerThenLoginReturnsJwt() throws Exception {
        register("alice@example.com", "secret123")
                .andExpect(status().isOk())
                .andExpect(content().string("Registered successfully"));

        login("alice@example.com", "secret123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.token").isNotEmpty());

        assertThat(users.findByEmail("alice@example.com")).hasValueSatisfying(
                u -> assertThat(u.getPassword()).startsWith("$2"));
    }

    @Test
    void duplicateRegistrationIsConflict() throws Exception {
        register("alice@example.com", "secret123").andExpect(status().isOk());

        register("alice@example.com", "another1").andExpect(status().isConflict());
    }

    @Test
    void invalidEmailIsRejected() throws Exception {
        register("not-an-email", "secret123")
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid email address"));
    }

    @Test
    void shortPasswordIsRejected() throws Exception {
        register("alice@example.com", "1234567")
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Password must be at least 8 characters"));
    }

    @Test
    void overlongPasswordIsRejected() throws Exception {
        register("alice@example.com", "a".repeat(73))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Password must be at most 72 characters"));
    }

    @Test
    void wrongPasswordIsUnauthorized() throws Exception {
        register("alice@example.com", "secret123");

        login("alice@example.com", "wrong-password").andExpect(status().isUnauthorized());
    }

    @Test
    void fiveWrongPasswordsLockTheAccount() throws Exception {
        register("locked@example.com", "secret123");
        for (int i = 0; i < 5; i++) {
            login("locked@example.com", "wrong-password").andExpect(status().isUnauthorized());
        }

        // Even the right password is refused while locked
        login("locked@example.com", "secret123")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "900"))
                .andExpect(content().string("Too many failed attempts. Try again in 15 minutes"));
    }

    @Test
    void successfulLoginResetsTheFailureCount() throws Exception {
        register("reset@example.com", "secret123");
        for (int i = 0; i < 4; i++) {
            login("reset@example.com", "wrong-password");
        }
        login("reset@example.com", "secret123").andExpect(status().isOk());
        for (int i = 0; i < 4; i++) {
            login("reset@example.com", "wrong-password");
        }

        login("reset@example.com", "secret123").andExpect(status().isOk());
    }

    @Test
    void unknownUserIsUnauthorized() throws Exception {
        login("nobody@example.com", "secret123").andExpect(status().isUnauthorized());
    }

    @Test
    void malformedBodyIsBadRequest() throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed request body"));
    }

    private org.springframework.test.web.servlet.ResultActions register(String email, String password) throws Exception {
        return mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }
}
