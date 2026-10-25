package com.mealminutes.platform.api;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTests {
    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() { MDC.clear(); }

    @Test
    void preservesSafeCorrelationIdAndRemovesMdcAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        request.addHeader(CorrelationIdFilter.HEADER, "req-123.ab");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isEqualTo("req-123.ab");
            assertThat(((MockHttpServletResponse) res).getHeader(CorrelationIdFilter.HEADER)).isEqualTo("req-123.ab");
        });

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void replacesUnsafeCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        request.addHeader(CorrelationIdFilter.HEADER, "bad\r\nX-Injected: yes");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).matches("[0-9a-f-]{36}");
    }

    @Test
    void removesMdcWhenDownstreamThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> filter.doFilter(request, response,
                (req, res) -> { throw new jakarta.servlet.ServletException("expected"); }))
                .isInstanceOf(jakarta.servlet.ServletException.class);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}