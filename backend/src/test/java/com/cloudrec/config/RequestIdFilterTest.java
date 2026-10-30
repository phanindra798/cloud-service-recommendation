package com.cloudrec.config;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void generatesIdPutsItInMdcAndResponseThenClears() throws Exception {
        AtomicReference<String> seenInChain = new AtomicReference<>();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/history"), response,
                (req, res) -> seenInChain.set(MDC.get(RequestIdFilter.MDC_KEY)));

        assertThat(seenInChain.get()).matches("[0-9a-f-]{36}");
        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo(seenInChain.get());
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void reusesSafeIncomingId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/history");
        request.addHeader(RequestIdFilter.HEADER, "trace-abc_123.4");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("trace-abc_123.4");
    }

    @Test
    void replacesUnsafeIncomingId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/history");
        request.addHeader(RequestIdFilter.HEADER, "evil\nInjected-Log-Line");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(response.getHeader(RequestIdFilter.HEADER)).matches("[0-9a-f-]{36}");
    }

    @Test
    void clearsMdcEvenWhenChainThrows() {
        try {
            filter.doFilter(new MockHttpServletRequest("GET", "/x"), new MockHttpServletResponse(),
                    (req, res) -> { throw new IllegalStateException("boom"); });
        } catch (Exception ignored) {
            // expected
        }
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }
}
