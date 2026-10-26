package com.mealminutes.platform.tenant.web;

import com.mealminutes.platform.tenant.domain.TenantContext;
import com.mealminutes.platform.tenant.domain.TenantResolutionException;
import com.mealminutes.platform.tenant.service.TenantResolverService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Component
public class TenantContextFilter extends OncePerRequestFilter {
    private final TenantResolverService tenantResolverService;

    public TenantContextFilter(TenantResolverService tenantResolverService) {
        this.tenantResolverService = tenantResolverService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/") || path.equals("/actuator")
                || path.startsWith("/api/v1/platform/") || path.startsWith("/api/v1/auth/") || path.equals("/api/v1/tenant-domains/verify");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        TenantContext.clear();
        try {
            var tenant = tenantResolverService.resolve(request.getServerName());
            try (TenantContext.Scope ignored = TenantContext.bind(tenant)) {
                filterChain.doFilter(request, response);
            }
        } catch (TenantResolutionException ex) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Tenant not found\",\"status\":404}");
        } finally {
            TenantContext.clear();
        }
    }
}