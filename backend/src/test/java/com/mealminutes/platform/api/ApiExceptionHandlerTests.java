package com.mealminutes.platform.api;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTests {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void mapsIllegalArgumentToBadRequestWithoutExposingStackTrace() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/example");
        request.addHeader(CorrelationIdFilter.HEADER, "corr-123");

        ProblemDetail problem = handler.handleBadRequest(new IllegalArgumentException("Invalid slug"), request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getTitle()).isEqualTo("Invalid request");
        assertThat(problem.getDetail()).isEqualTo("Invalid slug");
        assertThat(problem.getProperties()).containsEntry("correlationId", "corr-123");
    }

    @Test
    void hidesUnexpectedExceptionDetails() {
        HttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/example");

        ProblemDetail problem = handler.handleUnexpected(new IllegalStateException("password=secret"), request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getDetail()).doesNotContain("secret");
    }

    @Test
    void mapsStateConflict() {
        HttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/example");
        ProblemDetail problem = handler.handleConflict(new IllegalStateException("not allowed"), request);
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
    }
}