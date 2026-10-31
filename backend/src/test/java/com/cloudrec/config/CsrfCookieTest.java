package com.cloudrec.config;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The real CSRF round trip the SPA relies on. Runs in its own Spring context
 * because spring-security-test's csrf() helper (used elsewhere) swaps the
 * token repository in the shared context for one that writes no cookie.
 */
@SpringBootTest(properties = "spring.application.name=csrf-cookie-test")
@AutoConfigureMockMvc
class CsrfCookieTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void everyResponseIssuesAReadableCsrfCookie() throws Exception {
        MockHttpServletResponse response = mvc.perform(get("/auth/me")).andReturn().getResponse();

        Cookie xsrf = response.getCookie("XSRF-TOKEN");
        assertThat(xsrf).isNotNull();
        assertThat(xsrf.getValue()).isNotBlank();
        assertThat(xsrf.isHttpOnly()).as("the SPA must be able to read it").isFalse();
    }

    @Test
    void echoingTheCookieInTheHeaderPassesCsrf() throws Exception {
        Cookie xsrf = mvc.perform(get("/auth/me")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        Cookie session = new Cookie("auth_token", "not-a-valid-jwt");

        // Without the header: rejected by CSRF before anything else
        mvc.perform(post("/auth/logout").cookie(session, xsrf)).andExpect(status().isForbidden());

        // With the cookie value echoed in X-XSRF-TOKEN: accepted
        mvc.perform(post("/auth/logout").cookie(session, xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }
}
