package com.mealminutes.platform.tenant;

import com.mealminutes.platform.tenant.domain.TenantContext;
import com.mealminutes.platform.tenant.domain.TenantContext.TenantDescriptor;
import com.mealminutes.platform.tenant.domain.TenantResolutionException;
import com.mealminutes.platform.tenant.service.TenantResolverService;
import com.mealminutes.platform.tenant.web.TenantContextFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class TenantContextFilterTests {
    @AfterEach
    void clearContext() { TenantContext.clear(); }

    @Test
    void bindsTenantForRequestAndClearsItAfterSuccess() throws Exception {
        TenantResolverService resolver = mock(TenantResolverService.class);
        when(resolver.resolve("abc.example.com"))
                .thenReturn(new TenantDescriptor("tenant-1", "abc", "tenant_abc", "secret://abc"));
        TenantContextFilter filter = new TenantContextFilter(resolver);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/menu");
        request.setServerName("abc.example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> assertThat(TenantContext.requireCurrent().tenantId()).isEqualTo("tenant-1");

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(TenantContext.currentOrNull()).isNull();
    }

    @Test
    void returnsNotFoundForUnknownTenant() throws Exception {
        TenantResolverService resolver = mock(TenantResolverService.class);
        when(resolver.resolve("missing.example.com"))
                .thenThrow(new TenantResolutionException("Tenant could not be resolved"));
        TenantContextFilter filter = new TenantContextFilter(resolver);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/menu");
        request.setServerName("missing.example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentAsString()).contains("\"status\":404");
        verify(chain, never()).doFilter(any(), any());
        assertThat(TenantContext.currentOrNull()).isNull();
    }

    @Test
    void clearsContextWhenDownstreamThrows() throws Exception {
        TenantResolverService resolver = mock(TenantResolverService.class);
        when(resolver.resolve("abc.example.com"))
                .thenReturn(new TenantDescriptor("tenant-1", "abc", "tenant_abc", "secret://abc"));
        TenantContextFilter filter = new TenantContextFilter(resolver);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/menu");
        request.setServerName("abc.example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> { throw new ServletException("expected test exception"); };

        assertThrows(ServletException.class, () -> filter.doFilter(request, response, chain));
        assertThat(TenantContext.currentOrNull()).isNull();
    }

    @Test
    void skipsPlatformAndHealthPaths() throws IOException, ServletException {
        TenantResolverService resolver = mock(TenantResolverService.class);
        TenantContextFilter filter = new TenantContextFilter(resolver);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/actuator/health"), response, (req, res) -> { });
        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/platform/tenants"), response, (req, res) -> { });

        verifyNoInteractions(resolver);
    }
}